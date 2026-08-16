#!/usr/bin/env python3
"""
Verification script for Android 16 KB page size compatibility.
Checks:
1. All native shared libraries (.so) inside APK/AAB have ELF LOAD segment alignment >= 16384 bytes (16 KB).
2. All native shared libraries inside APK are stored uncompressed (STORED / method 0) and aligned to 16384 bytes boundary.
"""

import sys
import os
import zipfile
import struct

PAGE_SIZE_16KB = 16384

def parse_elf_load_align(data: bytes) -> int:
    """Parse ELF header and program headers to determine max LOAD segment alignment."""
    if len(data) < 64 or data[:4] != b'\x7fELF':
        return 0

    ei_class = data[4]  # 1 = 32-bit, 2 = 64-bit
    ei_data = data[5]   # 1 = little-endian, 2 = big-endian
    endian = '<' if ei_data == 1 else '>'

    max_align = 0

    if ei_class == 1:  # 32-bit ELF
        e_phoff = struct.unpack_from(f'{endian}I', data, 28)[0]
        e_phentsize = struct.unpack_from(f'{endian}H', data, 42)[0]
        e_phnum = struct.unpack_from(f'{endian}H', data, 44)[0]

        for i in range(e_phnum):
            offset = e_phoff + i * e_phentsize
            if offset + 32 > len(data):
                break
            p_type, p_offset, p_vaddr, p_paddr, p_filesz, p_memsz, p_flags, p_align = struct.unpack_from(
                f'{endian}IIIIIIII', data, offset
            )
            if p_type == 1:  # PT_LOAD
                if p_align > max_align:
                    max_align = p_align

    elif ei_class == 2:  # 64-bit ELF
        e_phoff = struct.unpack_from(f'{endian}Q', data, 32)[0]
        e_phentsize = struct.unpack_from(f'{endian}H', data, 54)[0]
        e_phnum = struct.unpack_from(f'{endian}H', data, 56)[0]

        for i in range(e_phnum):
            offset = e_phoff + i * e_phentsize
            if offset + 56 > len(data):
                break
            p_type, p_flags, p_offset, p_vaddr, p_paddr, p_filesz, p_memsz, p_align = struct.unpack_from(
                f'{endian}IIQQQQQQ', data, offset
            )
            if p_type == 1:  # PT_LOAD
                if p_align > max_align:
                    max_align = p_align

    return max_align

def verify_archive(archive_path: str) -> bool:
    print(f"\n=======================================================")
    print(f"Verifying 16 KB Page Size Compatibility for:")
    print(f"  {archive_path}")
    print(f"=======================================================")

    if not os.path.exists(archive_path):
        print(f"ERROR: File not found: {archive_path}")
        return False

    is_apk = archive_path.endswith('.apk')
    is_aab = archive_path.endswith('.aab')

    so_count = 0
    all_passed = True

    with zipfile.ZipFile(archive_path, 'r') as zf:
        with open(archive_path, 'rb') as raw_file:
            for info in zf.infolist():
                if not info.filename.endswith('.so'):
                    continue

                so_count += 1
                so_bytes = zf.read(info.filename)
                elf_align = parse_elf_load_align(so_bytes)

                # Check ELF segment alignment
                elf_ok = elf_align >= PAGE_SIZE_16KB
                elf_status = f"PASS (align = {elf_align} bytes / 0x{elf_align:x})" if elf_ok else f"FAIL (align = {elf_align} bytes < 16384)"

                print(f"\nLibrary: {info.filename}")
                print(f"  Size: {len(so_bytes)} bytes")
                print(f"  ELF LOAD Alignment: {elf_status}")

                if not elf_ok:
                    all_passed = False

                # For APKs, also check ZIP entry alignment and uncompressed storage
                if is_apk:
                    header_offset = info.header_offset
                    raw_file.seek(header_offset)
                    local_header = raw_file.read(30)
                    n_len = int.from_bytes(local_header[26:28], 'little')
                    e_len = int.from_bytes(local_header[28:30], 'little')
                    data_offset = header_offset + 30 + n_len + e_len

                    is_uncompressed = (info.compress_type == 0)
                    is_zip_aligned = (data_offset % PAGE_SIZE_16KB == 0)

                    compress_status = "PASS (STORED / uncompressed)" if is_uncompressed else f"FAIL (compressed, type={info.compress_type})"
                    zip_align_status = f"PASS (offset = {data_offset}, offset % 16384 == 0)" if is_zip_aligned else f"FAIL (offset = {data_offset}, offset % 16384 = {data_offset % PAGE_SIZE_16KB})"

                    print(f"  APK Compression:    {compress_status}")
                    print(f"  APK 16KB Alignment: {zip_align_status}")

                    if not is_uncompressed or not is_zip_aligned:
                        all_passed = False

    if so_count == 0:
        print("\nNo native libraries (.so) found in the archive.")
        return True

    print(f"\nSummary: Verified {so_count} native libraries.")
    if all_passed:
        print("RESULT: ALL NATIVE LIBRARIES FULLY SUPPORT 16 KB PAGE SIZE! \u2705")
    else:
        print("RESULT: 16 KB PAGE SIZE COMPLIANCE FAILED! \u274C")

    return all_passed

def main():
    if len(sys.argv) < 2:
        print("Usage: python verify_16kb_page_size.py <path-to-apk-or-aab> [<more-paths>...]")
        sys.exit(1)

    overall_success = True
    for path in sys.argv[1:]:
        if not verify_archive(path):
            overall_success = False

    if not overall_success:
        sys.exit(1)
    sys.exit(0)

if __name__ == '__main__':
    main()
