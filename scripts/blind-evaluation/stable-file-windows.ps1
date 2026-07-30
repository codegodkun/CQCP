$ErrorActionPreference = 'Stop'

$source = @'
using System;
using System.Collections.Generic;
using System.ComponentModel;
using System.IO;
using System.Runtime.InteropServices;
using Microsoft.Win32.SafeHandles;

namespace CqcpStableFile
{
    public sealed class StableReadResult
    {
        public byte[] Bytes { get; set; }
        public string FileIdentity { get; set; }
    }

    public static class Reader
    {
        private const uint FILE_READ_ATTRIBUTES = 0x00000080;
        private const uint FILE_READ_DATA = 0x00000001;
        private const uint FILE_TRAVERSE = 0x00000020;
        private const uint SYNCHRONIZE = 0x00100000;
        private const uint GENERIC_READ = 0x80000000;
        private const uint FILE_SHARE_READ = 0x00000001;
        private const uint FILE_SHARE_WRITE = 0x00000002;
        private const uint OPEN_EXISTING = 3;
        private const uint FILE_FLAG_BACKUP_SEMANTICS = 0x02000000;
        private const uint FILE_FLAG_OPEN_REPARSE_POINT = 0x00200000;
        private const uint FILE_ATTRIBUTE_DIRECTORY = 0x00000010;
        private const uint FILE_ATTRIBUTE_REPARSE_POINT = 0x00000400;
        private const uint OBJ_CASE_INSENSITIVE = 0x00000040;
        private const uint FILE_OPEN = 1;
        private const uint FILE_DIRECTORY_FILE = 0x00000001;
        private const uint FILE_SYNCHRONOUS_IO_NONALERT = 0x00000020;
        private const uint FILE_NON_DIRECTORY_FILE = 0x00000040;
        private const uint FILE_OPEN_REPARSE_POINT = 0x00200000;
        private const int STATUS_SUCCESS = 0;

        [StructLayout(LayoutKind.Sequential)]
        private struct FILETIME
        {
            public uint LowDateTime;
            public uint HighDateTime;
        }

        [StructLayout(LayoutKind.Sequential)]
        private struct BY_HANDLE_FILE_INFORMATION
        {
            public uint FileAttributes;
            public FILETIME CreationTime;
            public FILETIME LastAccessTime;
            public FILETIME LastWriteTime;
            public uint VolumeSerialNumber;
            public uint FileSizeHigh;
            public uint FileSizeLow;
            public uint NumberOfLinks;
            public uint FileIndexHigh;
            public uint FileIndexLow;
        }

        [StructLayout(LayoutKind.Sequential)]
        private struct UNICODE_STRING
        {
            public ushort Length;
            public ushort MaximumLength;
            public IntPtr Buffer;
        }

        [StructLayout(LayoutKind.Sequential)]
        private struct OBJECT_ATTRIBUTES
        {
            public int Length;
            public IntPtr RootDirectory;
            public IntPtr ObjectName;
            public uint Attributes;
            public IntPtr SecurityDescriptor;
            public IntPtr SecurityQualityOfService;
        }

        [StructLayout(LayoutKind.Sequential)]
        private struct IO_STATUS_BLOCK
        {
            public IntPtr Status;
            public IntPtr Information;
        }

        [DllImport("kernel32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
        private static extern SafeFileHandle CreateFileW(
            string fileName,
            uint desiredAccess,
            uint shareMode,
            IntPtr securityAttributes,
            uint creationDisposition,
            uint flagsAndAttributes,
            IntPtr templateFile);

        [DllImport("kernel32.dll", SetLastError = true)]
        [return: MarshalAs(UnmanagedType.Bool)]
        private static extern bool GetFileInformationByHandle(
            SafeFileHandle file,
            out BY_HANDLE_FILE_INFORMATION information);

        [DllImport("kernel32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
        private static extern uint GetFinalPathNameByHandleW(
            SafeFileHandle file,
            char[] path,
            uint pathLength,
            uint flags);

        [DllImport("ntdll.dll")]
        private static extern int NtCreateFile(
            out SafeFileHandle fileHandle,
            uint desiredAccess,
            ref OBJECT_ATTRIBUTES objectAttributes,
            out IO_STATUS_BLOCK ioStatusBlock,
            IntPtr allocationSize,
            uint fileAttributes,
            uint shareAccess,
            uint createDisposition,
            uint createOptions,
            IntPtr eaBuffer,
            uint eaLength);

        private static bool SafeName(string value)
        {
            return !string.IsNullOrWhiteSpace(value) &&
                value != "." &&
                value != ".." &&
                value.IndexOfAny(new[] {
                    Path.DirectorySeparatorChar,
                    Path.AltDirectorySeparatorChar,
                    ':',
                    '\0',
                    '\r',
                    '\n'
                }) < 0;
        }

        private static SafeFileHandle OpenDirectory(string path)
        {
            SafeFileHandle handle = CreateFileW(
                path,
                FILE_READ_DATA | FILE_TRAVERSE |
                    FILE_READ_ATTRIBUTES | SYNCHRONIZE,
                FILE_SHARE_READ | FILE_SHARE_WRITE,
                IntPtr.Zero,
                OPEN_EXISTING,
                FILE_FLAG_BACKUP_SEMANTICS | FILE_FLAG_OPEN_REPARSE_POINT,
                IntPtr.Zero);
            if (handle.IsInvalid)
            {
                throw new Win32Exception(Marshal.GetLastWin32Error());
            }
            return handle;
        }

        private static SafeFileHandle OpenRelative(
            SafeFileHandle root,
            string childName,
            bool directory)
        {
            if (!SafeName(childName))
            {
                throw new InvalidOperationException("UNSAFE_CHILD_NAME");
            }
            IntPtr nameBuffer = Marshal.StringToHGlobalUni(childName);
            IntPtr unicodePointer = IntPtr.Zero;
            try
            {
                var unicode = new UNICODE_STRING {
                    Length = checked((ushort)(childName.Length * 2)),
                    MaximumLength = checked((ushort)((childName.Length + 1) * 2)),
                    Buffer = nameBuffer
                };
                unicodePointer = Marshal.AllocHGlobal(
                    Marshal.SizeOf<UNICODE_STRING>());
                Marshal.StructureToPtr(unicode, unicodePointer, false);
                var attributes = new OBJECT_ATTRIBUTES {
                    Length = Marshal.SizeOf<OBJECT_ATTRIBUTES>(),
                    RootDirectory = root.DangerousGetHandle(),
                    ObjectName = unicodePointer,
                    Attributes = OBJ_CASE_INSENSITIVE,
                    SecurityDescriptor = IntPtr.Zero,
                    SecurityQualityOfService = IntPtr.Zero
                };
                uint options = FILE_SYNCHRONOUS_IO_NONALERT |
                    FILE_OPEN_REPARSE_POINT |
                    (directory ? FILE_DIRECTORY_FILE : FILE_NON_DIRECTORY_FILE);
                uint access = FILE_READ_ATTRIBUTES | SYNCHRONIZE |
                    (directory
                        ? FILE_READ_DATA | FILE_TRAVERSE
                        : GENERIC_READ);
                int status = NtCreateFile(
                    out var handle,
                    access,
                    ref attributes,
                    out var ioStatus,
                    IntPtr.Zero,
                    0,
                    directory
                        ? FILE_SHARE_READ | FILE_SHARE_WRITE
                        : FILE_SHARE_READ,
                    FILE_OPEN,
                    options,
                    IntPtr.Zero,
                    0);
                if (status != STATUS_SUCCESS || handle.IsInvalid)
                {
                    if (handle != null)
                    {
                        handle.Dispose();
                    }
                    throw new InvalidOperationException(
                        "NT_OPEN_FAILED:" + status.ToString("x8"));
                }
                return handle;
            }
            finally
            {
                if (unicodePointer != IntPtr.Zero)
                {
                    Marshal.FreeHGlobal(unicodePointer);
                }
                Marshal.FreeHGlobal(nameBuffer);
            }
        }

        private static BY_HANDLE_FILE_INFORMATION Information(
            SafeFileHandle handle)
        {
            if (!GetFileInformationByHandle(handle, out var information))
            {
                throw new Win32Exception(Marshal.GetLastWin32Error());
            }
            return information;
        }

        private static string FinalPath(SafeFileHandle handle)
        {
            var buffer = new char[32768];
            uint length = GetFinalPathNameByHandleW(
                handle,
                buffer,
                (uint)buffer.Length,
                0);
            if (length == 0 || length >= buffer.Length)
            {
                throw new Win32Exception(Marshal.GetLastWin32Error());
            }
            string value = new string(buffer, 0, (int)length);
            if (value.StartsWith(@"\\?\UNC\", StringComparison.OrdinalIgnoreCase))
            {
                value = @"\\" + value.Substring(8);
            }
            else if (value.StartsWith(@"\\?\", StringComparison.OrdinalIgnoreCase))
            {
                value = value.Substring(4);
            }
            return Path.GetFullPath(value)
                .TrimEnd(Path.DirectorySeparatorChar, Path.AltDirectorySeparatorChar);
        }

        private static bool SamePath(string left, string right)
        {
            return string.Equals(
                Path.GetFullPath(left)
                    .TrimEnd(Path.DirectorySeparatorChar, Path.AltDirectorySeparatorChar),
                Path.GetFullPath(right)
                    .TrimEnd(Path.DirectorySeparatorChar, Path.AltDirectorySeparatorChar),
                StringComparison.OrdinalIgnoreCase);
        }

        private static ulong Size(BY_HANDLE_FILE_INFORMATION information)
        {
            return ((ulong)information.FileSizeHigh << 32) |
                information.FileSizeLow;
        }

        private static string Identity(BY_HANDLE_FILE_INFORMATION information)
        {
            ulong index = ((ulong)information.FileIndexHigh << 32) |
                information.FileIndexLow;
            return information.VolumeSerialNumber.ToString("x8") + ":" +
                index.ToString("x16") + ":" + Size(information).ToString();
        }

        private static void RequireDirectory(
            SafeFileHandle handle,
            string expectedPath,
            string expectedParent)
        {
            var information = Information(handle);
            if ((information.FileAttributes & FILE_ATTRIBUTE_DIRECTORY) == 0 ||
                (information.FileAttributes & FILE_ATTRIBUTE_REPARSE_POINT) != 0)
            {
                throw new InvalidOperationException("UNSAFE_DIRECTORY_HANDLE");
            }
            string finalPath = FinalPath(handle);
            if (!SamePath(finalPath, expectedPath))
            {
                throw new InvalidOperationException("DIRECTORY_PATH_IDENTITY_MISMATCH");
            }
            if (expectedParent != null &&
                !SamePath(Path.GetDirectoryName(finalPath), expectedParent))
            {
                throw new InvalidOperationException("DIRECTORY_PARENT_MISMATCH");
            }
        }

        public static StableReadResult Read(
            string repoRoot,
            string requiredRoot,
            string childName,
            int maxBytes)
        {
            if (maxBytes <= 0 ||
                string.IsNullOrWhiteSpace(repoRoot) ||
                string.IsNullOrWhiteSpace(requiredRoot) ||
                string.IsNullOrWhiteSpace(childName) ||
                childName == "." ||
                childName == ".." ||
                childName.IndexOfAny(new[] {
                    Path.DirectorySeparatorChar,
                    Path.AltDirectorySeparatorChar,
                    '\0',
                    '\r',
                    '\n'
                }) >= 0)
            {
                throw new InvalidOperationException("INVALID_ARGUMENT");
            }

            string lexicalRepo = Path.GetFullPath(repoRoot)
                .TrimEnd(Path.DirectorySeparatorChar, Path.AltDirectorySeparatorChar);
            string lexicalRoot = Path.GetFullPath(requiredRoot)
                .TrimEnd(Path.DirectorySeparatorChar, Path.AltDirectorySeparatorChar);
            string relativeRoot = Path.GetRelativePath(lexicalRepo, lexicalRoot);
            if (relativeRoot == ".." ||
                relativeRoot.StartsWith(".." + Path.DirectorySeparatorChar) ||
                Path.IsPathRooted(relativeRoot))
            {
                throw new InvalidOperationException("ROOT_OUTSIDE_REPOSITORY");
            }

            var directories = new List<SafeFileHandle>();
            SafeFileHandle file = null;
            try
            {
                var repo = OpenDirectory(lexicalRepo);
                directories.Add(repo);
                RequireDirectory(repo, lexicalRepo, null);
                SafeFileHandle current = repo;
                string currentLexical = lexicalRepo;
                string currentFinal = FinalPath(repo);

                foreach (string segment in relativeRoot.Split(
                    new[] { Path.DirectorySeparatorChar, Path.AltDirectorySeparatorChar },
                    StringSplitOptions.RemoveEmptyEntries))
                {
                    if (segment == ".")
                    {
                        continue;
                    }
                    if (!SafeName(segment))
                    {
                        throw new InvalidOperationException("UNSAFE_ROOT_SEGMENT");
                    }
                    string nextLexical = Path.Combine(currentLexical, segment);
                    var next = OpenRelative(current, segment, true);
                    directories.Add(next);
                    RequireDirectory(next, nextLexical, currentFinal);
                    current = next;
                    currentLexical = nextLexical;
                    currentFinal = FinalPath(next);
                }

                if (!SamePath(currentLexical, lexicalRoot))
                {
                    throw new InvalidOperationException("ROOT_TRAVERSAL_MISMATCH");
                }

                string filePath = Path.Combine(lexicalRoot, childName);
                file = OpenRelative(
                    current,
                    childName,
                    false);
                var before = Information(file);
                if ((before.FileAttributes & FILE_ATTRIBUTE_DIRECTORY) != 0 ||
                    (before.FileAttributes & FILE_ATTRIBUTE_REPARSE_POINT) != 0 ||
                    Size(before) > (ulong)maxBytes ||
                    Size(before) > int.MaxValue)
                {
                    throw new InvalidOperationException("UNSAFE_FILE_HANDLE");
                }
                string finalFile = FinalPath(file);
                if (!SamePath(finalFile, filePath) ||
                    !SamePath(Path.GetDirectoryName(finalFile), currentFinal))
                {
                    throw new InvalidOperationException("FILE_PARENT_MISMATCH");
                }

                byte[] bytes = new byte[(int)Size(before)];
                using (var stream = new FileStream(file, FileAccess.Read, 4096, false))
                {
                    file = null;
                    int offset = 0;
                    while (offset < bytes.Length)
                    {
                        int read = stream.Read(bytes, offset, bytes.Length - offset);
                        if (read == 0)
                        {
                            throw new EndOfStreamException();
                        }
                        offset += read;
                    }
                    if (stream.ReadByte() != -1)
                    {
                        throw new InvalidOperationException("FILE_GREW_DURING_READ");
                    }
                    var after = Information(stream.SafeFileHandle);
                    if (Identity(before) != Identity(after))
                    {
                        throw new InvalidOperationException("FILE_IDENTITY_CHANGED");
                    }
                }
                return new StableReadResult {
                    Bytes = bytes,
                    FileIdentity = Identity(before)
                };
            }
            finally
            {
                if (file != null)
                {
                    file.Dispose();
                }
                for (int index = directories.Count - 1; index >= 0; index--)
                {
                    directories[index].Dispose();
                }
            }
        }
    }
}
'@

try {
    Add-Type -TypeDefinition $source -Language CSharp
    $BatchJsonBase64 = [Console]::In.ReadToEnd()
    if ([string]::IsNullOrWhiteSpace($BatchJsonBase64)) {
        throw 'EMPTY_BATCH_INPUT'
    }
    $batchJson = [Text.Encoding]::UTF8.GetString(
        [Convert]::FromBase64String($BatchJsonBase64)
    )
    $requests = @($batchJson | ConvertFrom-Json)
    if ($requests.Count -lt 1 -or $requests.Count -gt 512) {
        throw 'INVALID_BATCH_SIZE'
    }
    $results = @(
        foreach ($request in $requests) {
            $result = [CqcpStableFile.Reader]::Read(
                [string] $request.repoRoot,
                [string] $request.requiredRoot,
                [string] $request.childName,
                [int] $request.maxBytes
            )
            [pscustomobject]@{
                schemaVersion = 'cqcp-stable-file-read-v1'
                bytesBase64 = [Convert]::ToBase64String($result.Bytes)
                size = $result.Bytes.Length
                fileIdentity = $result.FileIdentity
                runtimeVersion = $PSVersionTable.PSVersion.ToString()
            }
        }
    )
    ConvertTo-Json -InputObject $results -Compress
}
catch {
    $category = [string] $_.Exception.Message
    if ($category -notmatch '^[A-Z0-9_:-]{1,96}$') {
        $category = 'UNCLASSIFIED'
    }
    [Console]::Error.WriteLine(
        'CQCP_STABLE_FILE_READ_FAILED:' + $category
    )
    exit 17
}
