import base64
import json
import os
import stat
import sys
import platform


def fail():
    print("CQCP_STABLE_FILE_READ_FAILED", file=sys.stderr)
    raise SystemExit(17)


def read_one(request):
    repo_root = request["repoRoot"]
    required_root = request["requiredRoot"]
    child_name = request["childName"]
    max_bytes_text = request["maxBytes"]
    try:
        max_bytes = int(max_bytes_text)
        if max_bytes <= 0:
            fail()
        if (
            not child_name
            or child_name in (".", "..")
            or "/" in child_name
            or "\\" in child_name
            or any(character in child_name for character in ("\0", "\r", "\n"))
        ):
            fail()
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
        directory_fds = []
        repo_fd = os.open(
            repo_root,
            os.O_RDONLY | directory | nofollow,
        )
        directory_fds.append(repo_fd)
        current_fd = repo_fd
        for segment in relative_root.split(os.sep):
            if segment == ".":
                continue
            if not segment or segment == "..":
                fail()
            current_fd = os.open(
                segment,
                os.O_RDONLY | directory | nofollow,
                dir_fd=current_fd,
            )
            directory_fds.append(current_fd)
        file_fd = os.open(
            child_name,
            os.O_RDONLY | nofollow,
            dir_fd=current_fd,
        )
        try:
            before = os.fstat(file_fd)
            if not stat.S_ISREG(before.st_mode) or before.st_size > max_bytes:
                fail()
            chunks = []
            total = 0
            while True:
                chunk = os.read(file_fd, min(65536, max_bytes + 1 - total))
                if not chunk:
                    break
                chunks.append(chunk)
                total += len(chunk)
                if total > max_bytes:
                    fail()
            after = os.fstat(file_fd)
            if (
                before.st_dev != after.st_dev
                or before.st_ino != after.st_ino
                or before.st_size != after.st_size
                or before.st_mtime_ns != after.st_mtime_ns
                or before.st_ctime_ns != after.st_ctime_ns
                or total != before.st_size
            ):
                fail()
            bytes_value = b"".join(chunks)
            return {
                "schemaVersion": "cqcp-stable-file-read-v1",
                "bytesBase64": base64.b64encode(bytes_value).decode("ascii"),
                "size": len(bytes_value),
                "fileIdentity": (
                    f"{before.st_dev:x}:{before.st_ino:x}:{before.st_size}"
                ),
                "runtimeVersion": platform.python_version(),
            }
        finally:
            os.close(file_fd)
            for descriptor in reversed(directory_fds):
                os.close(descriptor)
    except (OSError, ValueError, TypeError):
        fail()


def main():
    if len(sys.argv) != 1:
        fail()
    try:
        encoded = sys.stdin.read()
        if not encoded:
            fail()
        requests = json.loads(base64.b64decode(encoded).decode("utf-8"))
        if not isinstance(requests, list) or not 1 <= len(requests) <= 512:
            fail()
        print(
            json.dumps(
                [read_one(request) for request in requests],
                separators=(",", ":"),
            )
        )
    except (ValueError, TypeError, KeyError):
        fail()


if __name__ == "__main__":
    main()
