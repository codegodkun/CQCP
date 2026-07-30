import base64
import json
import os
import platform
import stat
import sys


def fail():
    print("CQCP_STABLE_FILE_CREATE_FAILED", file=sys.stderr)
    raise SystemExit(17)


def safe_segment(value):
    return (
        isinstance(value, str)
        and value not in ("", ".", "..")
        and not any(character in value for character in ("/", "\\", ":", "\0", "\r", "\n"))
    )


def open_root(repo_root, required_root):
    repo_root = os.path.abspath(repo_root)
    required_root = os.path.abspath(required_root)
    relative_root = os.path.relpath(required_root, repo_root)
    if (
        relative_root == ".."
        or relative_root.startswith("../")
        or os.path.isabs(relative_root)
    ):
        fail()
    nofollow = getattr(os, "O_NOFOLLOW", None)
    directory = getattr(os, "O_DIRECTORY", None)
    if nofollow is None or directory is None:
        fail()
    descriptors = []
    current_fd = os.open(repo_root, os.O_RDONLY | directory | nofollow)
    descriptors.append(current_fd)
    for segment in relative_root.split(os.sep):
        if segment == ".":
            continue
        if not safe_segment(segment):
            fail()
        current_fd = os.open(
            segment,
            os.O_RDONLY | directory | nofollow,
            dir_fd=current_fd,
        )
        descriptors.append(current_fd)
    return descriptors, current_fd


def create_file(parent_fd, child_name, bytes_value):
    nofollow = os.O_NOFOLLOW
    descriptor = os.open(
        child_name,
        os.O_WRONLY | os.O_CREAT | os.O_EXCL | nofollow,
        0o600,
        dir_fd=parent_fd,
    )
    try:
        before = os.fstat(descriptor)
        if not stat.S_ISREG(before.st_mode) or before.st_size != 0:
            fail()
        offset = 0
        while offset < len(bytes_value):
            offset += os.write(descriptor, bytes_value[offset:])
        os.fsync(descriptor)
        after = os.fstat(descriptor)
        if (
            before.st_dev != after.st_dev
            or before.st_ino != after.st_ino
            or after.st_size != len(bytes_value)
        ):
            fail()
        return {
            "childName": child_name,
            "size": after.st_size,
            "fileIdentity": f"{after.st_dev:x}:{after.st_ino:x}:{after.st_size}",
        }
    finally:
        os.close(descriptor)


def main():
    if len(sys.argv) > 2:
        fail()
    try:
        request_base64 = sys.argv[1] if len(sys.argv) == 2 else sys.stdin.read()
        request = json.loads(base64.b64decode(request_base64).decode("utf-8"))
        if (
            request.get("schemaVersion") != "cqcp-stable-file-create-request-v1"
            or request.get("mode") not in ("WRITE_EXISTING_ROOT", "CREATE_ROOT")
            or not isinstance(request.get("files"), list)
            or not 1 <= len(request["files"]) <= 64
        ):
            fail()
        descriptors, root_fd = open_root(
            request["repoRoot"],
            request["requiredRoot"],
        )
        try:
            target_fd = root_fd
            directory_name = request.get("directoryName")
            if request["mode"] == "CREATE_ROOT":
                if not safe_segment(directory_name):
                    fail()
                os.mkdir(directory_name, 0o700, dir_fd=root_fd)
                target_fd = os.open(
                    directory_name,
                    os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW,
                    dir_fd=root_fd,
                )
                descriptors.append(target_fd)
            elif directory_name is not None:
                fail()
            results = []
            seen = set()
            for entry in request["files"]:
                child_name = entry.get("childName")
                if not safe_segment(child_name) or child_name in seen:
                    fail()
                seen.add(child_name)
                bytes_value = base64.b64decode(
                    entry["bytesBase64"],
                    validate=True,
                )
                if len(bytes_value) != entry["size"]:
                    fail()
                results.append(create_file(target_fd, child_name, bytes_value))
            os.fsync(target_fd)
            print(
                json.dumps(
                    {
                        "schemaVersion": "cqcp-stable-file-create-result-v1",
                        "status": "CREATED",
                        "mode": request["mode"],
                        "files": results,
                        "runtimeVersion": platform.python_version(),
                    },
                    separators=(",", ":"),
                )
            )
        finally:
            for descriptor in reversed(descriptors):
                os.close(descriptor)
    except (OSError, ValueError, TypeError, KeyError):
        fail()


if __name__ == "__main__":
    main()
