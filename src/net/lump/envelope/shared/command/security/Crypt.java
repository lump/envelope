package net.lump.envelope.shared.command.security;

//
// Crypt
//
// Hacked up from on two C sources:
//
//  1. crypt.c (DES one-way encryption)
//  2. crypt-md5.c  (MD5 one-way encryption)
//
// crypt.c, by Eric Young for his DES library.
//  The library is available at pub/Crypto/DES at
//  ftp.psy.uq.oz.aueay@mincom.oz.au or eay@psych.psy.uq.oz.au
//
// crypt-md5.c, by Poul-Henning Kamp for FreeBSD, which was distributed
// with the following notice:
//
// ----------------------------------------------------------------------------
// "THE BEER-WARE LICENSE" (Revision 42):
// <phk@login.dknet.dk> wrote parts of this file.  As long as you retain this
// notice you can do whatever you want with this stuff. If we meet some day,
// and you think this stuff is worth it, you can buy me a beer in return.
//                                                     Poul-Henning Kamp
// ----------------------------------------------------------------------------
//

import java.security.MessageDigest;


/**
 * A static DES and MD5 password encryption library.
 *
 * @author Troy Bowman
 * @version $Id: Crypt.java,v 1.2 2009/10/02 22:06:23 troy Exp $
 */

public final class Crypt {
  private static final String I_TO_A64 =
    "./0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

  private static final String MAGIC = "$1$";

  private static final boolean[] SHIFTS2 = {
    false, false, true, true, true, true, true, true,
    false, true, true, true, true, true, true, false
  };

  private static final int[] CON_SALT = {
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01,
    0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09,
    0x0A, 0x0B, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0A,
    0x0B, 0x0C, 0x0D, 0x0E, 0x0F, 0x10, 0x11, 0x12,
    0x13, 0x14, 0x15, 0x16, 0x17, 0x18, 0x19, 0x1A,
    0x1B, 0x1C, 0x1D, 0x1E, 0x1F, 0x20, 0x21, 0x22,
    0x23, 0x24, 0x25, 0x20, 0x21, 0x22, 0x23, 0x24,
    0x25, 0x26, 0x27, 0x28, 0x29, 0x2A, 0x2B, 0x2C,
    0x2D, 0x2E, 0x2F, 0x30, 0x31, 0x32, 0x33, 0x34,
    0x35, 0x36, 0x37, 0x38, 0x39, 0x3A, 0x3B, 0x3C,
    0x3D, 0x3E, 0x3F, 0x00, 0x00, 0x00, 0x00, 0x00,
  };

  private static final int[] COV_2CHAR = {
    0x2E, 0x2F, 0x30, 0x31, 0x32, 0x33, 0x34, 0x35,
    0x36, 0x37, 0x38, 0x39, 0x41, 0x42, 0x43, 0x44,
    0x45, 0x46, 0x47, 0x48, 0x49, 0x4A, 0x4B, 0x4C,
    0x4D, 0x4E, 0x4F, 0x50, 0x51, 0x52, 0x53, 0x54,
    0x55, 0x56, 0x57, 0x58, 0x59, 0x5A, 0x61, 0x62,
    0x63, 0x64, 0x65, 0x66, 0x67, 0x68, 0x69, 0x6A,
    0x6B, 0x6C, 0x6D, 0x6E, 0x6F, 0x70, 0x71, 0x72,
    0x73, 0x74, 0x75, 0x76, 0x77, 0x78, 0x79, 0x7A
  };

  private static final int ITERATIONS = 16;

  private static final int[][] SKB = {
    {
      /* for C bits (numbered as per FIPS 46) 1 2 3 4 5 6 */
      0x00000000, 0x00000010, 0x20000000, 0x20000010,
      0x00010000, 0x00010010, 0x20010000, 0x20010010,
      0x00000800, 0x00000810, 0x20000800, 0x20000810,
      0x00010800, 0x00010810, 0x20010800, 0x20010810,
      0x00000020, 0x00000030, 0x20000020, 0x20000030,
      0x00010020, 0x00010030, 0x20010020, 0x20010030,
      0x00000820, 0x00000830, 0x20000820, 0x20000830,
      0x00010820, 0x00010830, 0x20010820, 0x20010830,
      0x00080000, 0x00080010, 0x20080000, 0x20080010,
      0x00090000, 0x00090010, 0x20090000, 0x20090010,
      0x00080800, 0x00080810, 0x20080800, 0x20080810,
      0x00090800, 0x00090810, 0x20090800, 0x20090810,
      0x00080020, 0x00080030, 0x20080020, 0x20080030,
      0x00090020, 0x00090030, 0x20090020, 0x20090030,
      0x00080820, 0x00080830, 0x20080820, 0x20080830,
      0x00090820, 0x00090830, 0x20090820, 0x20090830,
    },
    {
      /* for C bits (numbered as per FIPS 46) 7 8 10 11 12 13 */
      0x00000000, 0x02000000, 0x00002000, 0x02002000,
      0x00200000, 0x02200000, 0x00202000, 0x02202000,
      0x00000004, 0x02000004, 0x00002004, 0x02002004,
      0x00200004, 0x02200004, 0x00202004, 0x02202004,
      0x00000400, 0x02000400, 0x00002400, 0x02002400,
      0x00200400, 0x02200400, 0x00202400, 0x02202400,
      0x00000404, 0x02000404, 0x00002404, 0x02002404,
      0x00200404, 0x02200404, 0x00202404, 0x02202404,
      0x10000000, 0x12000000, 0x10002000, 0x12002000,
      0x10200000, 0x12200000, 0x10202000, 0x12202000,
      0x10000004, 0x12000004, 0x10002004, 0x12002004,
      0x10200004, 0x12200004, 0x10202004, 0x12202004,
      0x10000400, 0x12000400, 0x10002400, 0x12002400,
      0x10200400, 0x12200400, 0x10202400, 0x12202400,
      0x10000404, 0x12000404, 0x10002404, 0x12002404,
      0x10200404, 0x12200404, 0x10202404, 0x12202404,
    },
    {
      /* for C bits (numbered as per FIPS 46) 14 15 16 17 19 20 */
      0x00000000, 0x00000001, 0x00040000, 0x00040001,
      0x01000000, 0x01000001, 0x01040000, 0x01040001,
      0x00000002, 0x00000003, 0x00040002, 0x00040003,
      0x01000002, 0x01000003, 0x01040002, 0x01040003,
      0x00000200, 0x00000201, 0x00040200, 0x00040201,
      0x01000200, 0x01000201, 0x01040200, 0x01040201,
      0x00000202, 0x00000203, 0x00040202, 0x00040203,
      0x01000202, 0x01000203, 0x01040202, 0x01040203,
      0x08000000, 0x08000001, 0x08040000, 0x08040001,
      0x09000000, 0x09000001, 0x09040000, 0x09040001,
      0x08000002, 0x08000003, 0x08040002, 0x08040003,
      0x09000002, 0x09000003, 0x09040002, 0x09040003,
      0x08000200, 0x08000201, 0x08040200, 0x08040201,
      0x09000200, 0x09000201, 0x09040200, 0x09040201,
      0x08000202, 0x08000203, 0x08040202, 0x08040203,
      0x09000202, 0x09000203, 0x09040202, 0x09040203,
    },
    {
      /* for C bits (numbered as per FIPS 46) 21 23 24 26 27 28 */
      0x00000000, 0x00100000, 0x00000100, 0x00100100,
      0x00000008, 0x00100008, 0x00000108, 0x00100108,
      0x00001000, 0x00101000, 0x00001100, 0x00101100,
      0x00001008, 0x00101008, 0x00001108, 0x00101108,
      0x04000000, 0x04100000, 0x04000100, 0x04100100,
      0x04000008, 0x04100008, 0x04000108, 0x04100108,
      0x04001000, 0x04101000, 0x04001100, 0x04101100,
      0x04001008, 0x04101008, 0x04001108, 0x04101108,
      0x00020000, 0x00120000, 0x00020100, 0x00120100,
      0x00020008, 0x00120008, 0x00020108, 0x00120108,
      0x00021000, 0x00121000, 0x00021100, 0x00121100,
      0x00021008, 0x00121008, 0x00021108, 0x00121108,
      0x04020000, 0x04120000, 0x04020100, 0x04120100,
      0x04020008, 0x04120008, 0x04020108, 0x04120108,
      0x04021000, 0x04121000, 0x04021100, 0x04121100,
      0x04021008, 0x04121008, 0x04021108, 0x04121108,
    },
    {
      /* for D bits (numbered as per FIPS 46) 1 2 3 4 5 6 */
      0x00000000, 0x10000000, 0x00010000, 0x10010000,
      0x00000004, 0x10000004, 0x00010004, 0x10010004,
      0x20000000, 0x30000000, 0x20010000, 0x30010000,
      0x20000004, 0x30000004, 0x20010004, 0x30010004,
      0x00100000, 0x10100000, 0x00110000, 0x10110000,
      0x00100004, 0x10100004, 0x00110004, 0x10110004,
      0x20100000, 0x30100000, 0x20110000, 0x30110000,
      0x20100004, 0x30100004, 0x20110004, 0x30110004,
      0x00001000, 0x10001000, 0x00011000, 0x10011000,
      0x00001004, 0x10001004, 0x00011004, 0x10011004,
      0x20001000, 0x30001000, 0x20011000, 0x30011000,
      0x20001004, 0x30001004, 0x20011004, 0x30011004,
      0x00101000, 0x10101000, 0x00111000, 0x10111000,
      0x00101004, 0x10101004, 0x00111004, 0x10111004,
      0x20101000, 0x30101000, 0x20111000, 0x30111000,
      0x20101004, 0x30101004, 0x20111004, 0x30111004,
    },
    {
      /* for D bits (numbered as per FIPS 46) 8 9 11 12 13 14 */
      0x00000000, 0x08000000, 0x00000008, 0x08000008,
      0x00000400, 0x08000400, 0x00000408, 0x08000408,
      0x00020000, 0x08020000, 0x00020008, 0x08020008,
      0x00020400, 0x08020400, 0x00020408, 0x08020408,
      0x00000001, 0x08000001, 0x00000009, 0x08000009,
      0x00000401, 0x08000401, 0x00000409, 0x08000409,
      0x00020001, 0x08020001, 0x00020009, 0x08020009,
      0x00020401, 0x08020401, 0x00020409, 0x08020409,
      0x02000000, 0x0A000000, 0x02000008, 0x0A000008,
      0x02000400, 0x0A000400, 0x02000408, 0x0A000408,
      0x02020000, 0x0A020000, 0x02020008, 0x0A020008,
      0x02020400, 0x0A020400, 0x02020408, 0x0A020408,
      0x02000001, 0x0A000001, 0x02000009, 0x0A000009,
      0x02000401, 0x0A000401, 0x02000409, 0x0A000409,
      0x02020001, 0x0A020001, 0x02020009, 0x0A020009,
      0x02020401, 0x0A020401, 0x02020409, 0x0A020409,
    },
    {
      /* for D bits (numbered as per FIPS 46) 16 17 18 19 20 21 */
      0x00000000, 0x00000100, 0x00080000, 0x00080100,
      0x01000000, 0x01000100, 0x01080000, 0x01080100,
      0x00000010, 0x00000110, 0x00080010, 0x00080110,
      0x01000010, 0x01000110, 0x01080010, 0x01080110,
      0x00200000, 0x00200100, 0x00280000, 0x00280100,
      0x01200000, 0x01200100, 0x01280000, 0x01280100,
      0x00200010, 0x00200110, 0x00280010, 0x00280110,
      0x01200010, 0x01200110, 0x01280010, 0x01280110,
      0x00000200, 0x00000300, 0x00080200, 0x00080300,
      0x01000200, 0x01000300, 0x01080200, 0x01080300,
      0x00000210, 0x00000310, 0x00080210, 0x00080310,
      0x01000210, 0x01000310, 0x01080210, 0x01080310,
      0x00200200, 0x00200300, 0x00280200, 0x00280300,
      0x01200200, 0x01200300, 0x01280200, 0x01280300,
      0x00200210, 0x00200310, 0x00280210, 0x00280310,
      0x01200210, 0x01200310, 0x01280210, 0x01280310,
    },
    {
      /* for D bits (numbered as per FIPS 46) 22 23 24 25 27 28 */
      0x00000000, 0x04000000, 0x00040000, 0x04040000,
      0x00000002, 0x04000002, 0x00040002, 0x04040002,
      0x00002000, 0x04002000, 0x00042000, 0x04042000,
      0x00002002, 0x04002002, 0x00042002, 0x04042002,
      0x00000020, 0x04000020, 0x00040020, 0x04040020,
      0x00000022, 0x04000022, 0x00040022, 0x04040022,
      0x00002020, 0x04002020, 0x00042020, 0x04042020,
      0x00002022, 0x04002022, 0x00042022, 0x04042022,
      0x00000800, 0x04000800, 0x00040800, 0x04040800,
      0x00000802, 0x04000802, 0x00040802, 0x04040802,
      0x00002800, 0x04002800, 0x00042800, 0x04042800,
      0x00002802, 0x04002802, 0x00042802, 0x04042802,
      0x00000820, 0x04000820, 0x00040820, 0x04040820,
      0x00000822, 0x04000822, 0x00040822, 0x04040822,
      0x00002820, 0x04002820, 0x00042820, 0x04042820,
      0x00002822, 0x04002822, 0x00042822, 0x04042822,
    },
  };

  private static final int[][] SP_TRANS = {
    {
      /* nibble 0 */
      0x00820200, 0x00020000, 0x80800000, 0x80820200,
      0x00800000, 0x80020200, 0x80020000, 0x80800000,
      0x80020200, 0x00820200, 0x00820000, 0x80000200,
      0x80800200, 0x00800000, 0x00000000, 0x80020000,
      0x00020000, 0x80000000, 0x00800200, 0x00020200,
      0x80820200, 0x00820000, 0x80000200, 0x00800200,
      0x80000000, 0x00000200, 0x00020200, 0x80820000,
      0x00000200, 0x80800200, 0x80820000, 0x00000000,
      0x00000000, 0x80820200, 0x00800200, 0x80020000,
      0x00820200, 0x00020000, 0x80000200, 0x00800200,
      0x80820000, 0x00000200, 0x00020200, 0x80800000,
      0x80020200, 0x80000000, 0x80800000, 0x00820000,
      0x80820200, 0x00020200, 0x00820000, 0x80800200,
      0x00800000, 0x80000200, 0x80020000, 0x00000000,
      0x00020000, 0x00800000, 0x80800200, 0x00820200,
      0x80000000, 0x80820000, 0x00000200, 0x80020200,
    },
    {
      /* nibble 1 */
      0x10042004, 0x00000000, 0x00042000, 0x10040000,
      0x10000004, 0x00002004, 0x10002000, 0x00042000,
      0x00002000, 0x10040004, 0x00000004, 0x10002000,
      0x00040004, 0x10042000, 0x10040000, 0x00000004,
      0x00040000, 0x10002004, 0x10040004, 0x00002000,
      0x00042004, 0x10000000, 0x00000000, 0x00040004,
      0x10002004, 0x00042004, 0x10042000, 0x10000004,
      0x10000000, 0x00040000, 0x00002004, 0x10042004,
      0x00040004, 0x10042000, 0x10002000, 0x00042004,
      0x10042004, 0x00040004, 0x10000004, 0x00000000,
      0x10000000, 0x00002004, 0x00040000, 0x10040004,
      0x00002000, 0x10000000, 0x00042004, 0x10002004,
      0x10042000, 0x00002000, 0x00000000, 0x10000004,
      0x00000004, 0x10042004, 0x00042000, 0x10040000,
      0x10040004, 0x00040000, 0x00002004, 0x10002000,
      0x10002004, 0x00000004, 0x10040000, 0x00042000,
    },
    {
      /* nibble 2 */
      0x41000000, 0x01010040, 0x00000040, 0x41000040,
      0x40010000, 0x01000000, 0x41000040, 0x00010040,
      0x01000040, 0x00010000, 0x01010000, 0x40000000,
      0x41010040, 0x40000040, 0x40000000, 0x41010000,
      0x00000000, 0x40010000, 0x01010040, 0x00000040,
      0x40000040, 0x41010040, 0x00010000, 0x41000000,
      0x41010000, 0x01000040, 0x40010040, 0x01010000,
      0x00010040, 0x00000000, 0x01000000, 0x40010040,
      0x01010040, 0x00000040, 0x40000000, 0x00010000,
      0x40000040, 0x40010000, 0x01010000, 0x41000040,
      0x00000000, 0x01010040, 0x00010040, 0x41010000,
      0x40010000, 0x01000000, 0x41010040, 0x40000000,
      0x40010040, 0x41000000, 0x01000000, 0x41010040,
      0x00010000, 0x01000040, 0x41000040, 0x00010040,
      0x01000040, 0x00000000, 0x41010000, 0x40000040,
      0x41000000, 0x40010040, 0x00000040, 0x01010000,
    },
    {
      /* nibble 3 */
      0x00100402, 0x04000400, 0x00000002, 0x04100402,
      0x00000000, 0x04100000, 0x04000402, 0x00100002,
      0x04100400, 0x04000002, 0x04000000, 0x00000402,
      0x04000002, 0x00100402, 0x00100000, 0x04000000,
      0x04100002, 0x00100400, 0x00000400, 0x00000002,
      0x00100400, 0x04000402, 0x04100000, 0x00000400,
      0x00000402, 0x00000000, 0x00100002, 0x04100400,
      0x04000400, 0x04100002, 0x04100402, 0x00100000,
      0x04100002, 0x00000402, 0x00100000, 0x04000002,
      0x00100400, 0x04000400, 0x00000002, 0x04100000,
      0x04000402, 0x00000000, 0x00000400, 0x00100002,
      0x00000000, 0x04100002, 0x04100400, 0x00000400,
      0x04000000, 0x04100402, 0x00100402, 0x00100000,
      0x04100402, 0x00000002, 0x04000400, 0x00100402,
      0x00100002, 0x00100400, 0x04100000, 0x04000402,
      0x00000402, 0x04000000, 0x04000002, 0x04100400,
    },
    {
      /* nibble 4 */
      0x02000000, 0x00004000, 0x00000100, 0x02004108,
      0x02004008, 0x02000100, 0x00004108, 0x02004000,
      0x00004000, 0x00000008, 0x02000008, 0x00004100,
      0x02000108, 0x02004008, 0x02004100, 0x00000000,
      0x00004100, 0x02000000, 0x00004008, 0x00000108,
      0x02000100, 0x00004108, 0x00000000, 0x02000008,
      0x00000008, 0x02000108, 0x02004108, 0x00004008,
      0x02004000, 0x00000100, 0x00000108, 0x02004100,
      0x02004100, 0x02000108, 0x00004008, 0x02004000,
      0x00004000, 0x00000008, 0x02000008, 0x02000100,
      0x02000000, 0x00004100, 0x02004108, 0x00000000,
      0x00004108, 0x02000000, 0x00000100, 0x00004008,
      0x02000108, 0x00000100, 0x00000000, 0x02004108,
      0x02004008, 0x02004100, 0x00000108, 0x00004000,
      0x00004100, 0x02004008, 0x02000100, 0x00000108,
      0x00000008, 0x00004108, 0x02004000, 0x02000008,
    },
    {
      /* nibble 5 */
      0x20000010, 0x00080010, 0x00000000, 0x20080800,
      0x00080010, 0x00000800, 0x20000810, 0x00080000,
      0x00000810, 0x20080810, 0x00080800, 0x20000000,
      0x20000800, 0x20000010, 0x20080000, 0x00080810,
      0x00080000, 0x20000810, 0x20080010, 0x00000000,
      0x00000800, 0x00000010, 0x20080800, 0x20080010,
      0x20080810, 0x20080000, 0x20000000, 0x00000810,
      0x00000010, 0x00080800, 0x00080810, 0x20000800,
      0x00000810, 0x20000000, 0x20000800, 0x00080810,
      0x20080800, 0x00080010, 0x00000000, 0x20000800,
      0x20000000, 0x00000800, 0x20080010, 0x00080000,
      0x00080010, 0x20080810, 0x00080800, 0x00000010,
      0x20080810, 0x00080800, 0x00080000, 0x20000810,
      0x20000010, 0x20080000, 0x00080810, 0x00000000,
      0x00000800, 0x20000010, 0x20000810, 0x20080800,
      0x20080000, 0x00000810, 0x00000010, 0x20080010,
    },
    {
      /* nibble 6 */
      0x00001000, 0x00000080, 0x00400080, 0x00400001,
      0x00401081, 0x00001001, 0x00001080, 0x00000000,
      0x00400000, 0x00400081, 0x00000081, 0x00401000,
      0x00000001, 0x00401080, 0x00401000, 0x00000081,
      0x00400081, 0x00001000, 0x00001001, 0x00401081,
      0x00000000, 0x00400080, 0x00400001, 0x00001080,
      0x00401001, 0x00001081, 0x00401080, 0x00000001,
      0x00001081, 0x00401001, 0x00000080, 0x00400000,
      0x00001081, 0x00401000, 0x00401001, 0x00000081,
      0x00001000, 0x00000080, 0x00400000, 0x00401001,
      0x00400081, 0x00001081, 0x00001080, 0x00000000,
      0x00000080, 0x00400001, 0x00000001, 0x00400080,
      0x00000000, 0x00400081, 0x00400080, 0x00001080,
      0x00000081, 0x00001000, 0x00401081, 0x00400000,
      0x00401080, 0x00000001, 0x00001001, 0x00401081,
      0x00400001, 0x00401080, 0x00401000, 0x00001001,
    },
    {
      /* nibble 7 */
      0x08200020, 0x08208000, 0x00008020, 0x00000000,
      0x08008000, 0x00200020, 0x08200000, 0x08208020,
      0x00000020, 0x08000000, 0x00208000, 0x00008020,
      0x00208020, 0x08008020, 0x08000020, 0x08200000,
      0x00008000, 0x00208020, 0x00200020, 0x08008000,
      0x08208020, 0x08000020, 0x00000000, 0x00208000,
      0x08000000, 0x00200000, 0x08008020, 0x08200020,
      0x00200000, 0x00008000, 0x08208000, 0x00000020,
      0x00200000, 0x00008000, 0x08000020, 0x08208020,
      0x00008020, 0x08000000, 0x00000000, 0x00208000,
      0x08200020, 0x08008020, 0x08008000, 0x00200020,
      0x08208000, 0x00000020, 0x00200020, 0x08008000,
      0x08208020, 0x00200000, 0x08200000, 0x08000020,
      0x00208000, 0x00008020, 0x08008020, 0x08200000,
      0x00000020, 0x08208000, 0x00208020, 0x00000000,
      0x08000000, 0x08200020, 0x00008000, 0x00208020
    }
  };


  /* this is a static class */
  private Crypt() {
  }

  /**
   * Depending on the provided salt, encrypts a password with either DES or MD5.
   *
   * @param inSalt   The DES or MD5 salt. <p>To be sure you get MD5 encryption, encase the salt between "$1$" and "$". Providing the
   *                 original MD5 hash as a salt accomplishes this.</p> <p>Salts will be treated as DES salts if they are found
   *                 without the above enclosing strings around them, provided they are the following lengths: <ul><li>2 characters
   *                 long: the size of a DES salt</li> <li>13 characters long: the size of a DES salt and hash combination</li></ul>
   *                 </p> <p>If the salt provided is not one of the above lengths: <ul><li>it will be treated as an md5 salt</li>
   *                 <li>if it is longer than 6 characters, it will be truncated to 6.</li> </ul></p><p>&nbsp;</p>
   * @param password The password. The DES algorithm will only pay attention to the first 64 bits (8 characters). The MD5 algorithm
   *                 pays attention to the first 2^64 bits.
   *
   * @return The encrypted password
   *
   * @throws java.security.NoSuchAlgorithmException
   *          when it can't find it.
   */
  public static String crypt(String inSalt, String password)
    throws java.security.NoSuchAlgorithmException {
    final String salt = yankSalt(inSalt);

    // some extra checks to make sure we know which is which
    if (!inSalt.startsWith(MAGIC)
      && (salt.length() == 2)
      && (inSalt.length() == 13 || inSalt.length() == 2)) {
      return desCrypt(salt, password);
    } else {
      return md5Crypt(salt, password);
    }
  }

  /**
   * Encrypts a password using DES.
   *
   * @param salt     The entropy.
   * @param original The cleartext password to be encrypted.
   *
   * @return The encrypted password.
   */
  @SuppressWarnings({"FinalStaticMethod"})
  public static final String desCrypt(String salt, String original) {
    while (salt.length() < 2) {
      salt += "A";
    }

    final StringBuffer buffer = new StringBuffer("             ");

    final char charZero = salt.charAt(0);
    final char charOne = salt.charAt(1);

    buffer.setCharAt(0, charZero);
    buffer.setCharAt(1, charOne);

    final int eSwap0 = CON_SALT[(int)charZero];
    final int eSwap1 = CON_SALT[(int)charOne] << 4;

    final byte[] key = new byte[8];

    for (int i = 0; i < key.length; i++) {
      key[i] = (byte)0;
    }

    for (int i = 0; i < key.length && i < original.length(); i++) {
      final int iChar = (int)original.charAt(i);

      key[i] = (byte)(iChar << 1);
    }

    final int[] schedule = desSetKey(key);
    final int[] out = body(schedule, eSwap0, eSwap1);

    final byte[] b = new byte[9];

    intToFourBytes(out[0], b, 0);
    intToFourBytes(out[1], b, 4);
    b[8] = 0;

    int y = 0;
    int u = 0x80;
    for (int i = 2; i < 13; i++) {
      int c = 0;
      for (int j = 0; j < 6; j++) {
        c <<= 1;

        if (((int)b[y] & u) != 0) {
          c |= 1;
        }

        u >>>= 1;

        if (u == 0) {
          y++;
          u = 0x80;
        }
        buffer.setCharAt(i, (char)COV_2CHAR[c]);
      }
    }
    return buffer.toString();
  }

  /**
   * Generates a random DES salt.
   *
   * @return a String which contains randomly generated DES salt.
   */
  public static String generateNewDesSalt() {
    return generateNewSalt(2);
  }

  /**
   * Generates a random MD5 salt.
   *
   * @return a String which contains randomly generated MD5 salt.
   */
  public static String generateNewMd5Salt() {
    return MAGIC + generateNewSalt(8) + "$";
  }

  /**
   * Encrypts a password using FreeBSD-style md5-based encryption.
   *
   * @param salt     The salt used to add some entropy to the encryption
   * @param password The cleartext password to be encrypted
   *
   * @return The encrypted password, or an empty string on error
   *
   * @throws java.security.NoSuchAlgorithmException
   *          if java.security does not support MD5
   */
  @SuppressWarnings({"UnusedAssignment"})
  public static String md5Crypt(String salt, String password)
    throws java.security.NoSuchAlgorithmException {
    /* First get the salt into a proper format.  It can be no more than
    * 8 characters, and if it starts with the MAGIC string, it should
    * be skipped.
    */

    salt = yankSalt(salt);

    /* now we have a properly formatted salt */

    MessageDigest md5v1;
    MessageDigest md5v2;

    md5v1 = MessageDigest.getInstance("MD5");

    /* First we update one MD5 with the password, MAGIC string, and salt */
    md5v1.update(password.getBytes());
    md5v1.update(MAGIC.getBytes());
    md5v1.update(salt.getBytes());

    md5v2 = MessageDigest.getInstance("MD5");

    /* Now start a second MD5 with the password, salt, and password again */
    md5v2.update(password.getBytes());
    md5v2.update(salt.getBytes());
    md5v2.update(password.getBytes());

    byte[] md5v2Digest = md5v2.digest();

    final int md5Size = md5v2Digest.length; // XXX
    int pwLength = password.length();

    /* Update the first MD5 a few times starting at the first
    * character of the second MD5 digest using the smaller
    * of the MD5 length or password length as the number of
    * bytes to use in the update.
    */
    for (int i = pwLength; i > 0; i -= md5Size) {
      md5v1.update(md5v2Digest, 0, i > md5Size ? md5Size : i);
    }

    /* the FreeBSD code does a memset to 0 on "final" (md5v2Digest) here
    * which may be a bug, since it references "final" again if the
    * conditional below is true, meaning it always is equal to 0
    */

    md5v2.reset();

    /* Again, update the first MD5 a few times, this time
    * using either 0 (see above) or the first byte of the
    * password, depending on the lowest order bit's value
    */
    byte[] pwBytes = password.getBytes();
    for (int i = pwLength; i > 0; i >>= 1) {
      if ((i & 1) == 1) {
        md5v1.update((byte)0);
      } else {
        md5v1.update(pwBytes[0]);
      }
    }

    /* Set up the output string. It'll look something like
    * $1$salt$ to begin with
    */
    final StringBuffer output = new StringBuffer(MAGIC);
    output.append(salt);
    output.append("$");

    byte[] md5v1Digest = md5v1.digest();

    /* According to the original source, this bit of madness
    * is introduced to slow things down.  It also further
    * mutates the result.
    */
    byte[] saltBytes = salt.getBytes();
    for (int i = 0; i < 1000; i++) {
      md5v2.reset();
      if ((i & 1) == 1) {
        md5v2.update(pwBytes);
      } else {
        md5v2.update(md5v1Digest);
      }
      if (i % 3 != 0) {
        md5v2.update(saltBytes);
      }
      if (i % 7 != 0) {
        md5v2.update(pwBytes);
      }
      if ((i & 1) != 0) {
        md5v2.update(md5v1Digest);
      } else {
        md5v2.update(pwBytes);
      }
      md5v1Digest = md5v2.digest();
    }

    /* Reorder the bytes in the digest and convert them to base64 */
    int value;
    value = ((md5v1Digest[0] & 0xff) << 16) | ((md5v1Digest[6] & 0xff) << 8) | (
      md5v1Digest[12]
        & 0xff);
    output.append(cryptTo64(value, 4));
    value = ((md5v1Digest[1] & 0xff) << 16) | ((md5v1Digest[7] & 0xff) << 8) | (
      md5v1Digest[13]
        & 0xff);
    output.append(cryptTo64(value, 4));
    value = ((md5v1Digest[2] & 0xff) << 16) | ((md5v1Digest[8] & 0xff) << 8) | (
      md5v1Digest[14]
        & 0xff);
    output.append(cryptTo64(value, 4));
    value = ((md5v1Digest[3] & 0xff) << 16) | ((md5v1Digest[9] & 0xff) << 8) | (
      md5v1Digest[15]
        & 0xff);
    output.append(cryptTo64(value, 4));
    value =
      ((md5v1Digest[4] & 0xff) << 16) | ((md5v1Digest[10] & 0xff) << 8) | (
        md5v1Digest[5]
          & 0xff);
    output.append(cryptTo64(value, 4));
    value = md5v1Digest[11] & 0xff;
    output.append(cryptTo64(value, 2));

    /* Drop some hints to the Garbage Collector */
    md5v1 = null;
    md5v2 = null;
    md5v1Digest = null;
    md5v2Digest = null;
    pwBytes = null;
    saltBytes = null;
    password = "";
    salt = "";
    pwLength = 0;

    return output.toString();
  }

  /**
   * Yanks out the salt part of a provided string.
   *
   * @param inSalt salt or password hash.
   *
   * @return the raw salt string.
   */
  @SuppressWarnings({"RedundantStringConstructorCall"})
  public static String yankSalt(String inSalt) {
    // play with our own copy
    String salt = new String(inSalt);

    // if there are no dollar signs, and lengths are
    // well known lengths for DES salts or hashes,
    // this is a DES salt.
    if (salt.indexOf('$') == -1) {
      if (salt.length() == 2) {
        return salt;
      }

      // Parse the salt from the DES hash.
      if (salt.length() == 13) {
        return salt.substring(0, 2);
      }
    }

    // the salt must have not been a DES salt if we are here.
    // Lets just parse it as if it were an MD5 salt.
    if (salt.startsWith(MAGIC)) {
      salt = salt.substring(MAGIC.length());
    }

    final int saltEnd = salt.indexOf('$');
    if (saltEnd != -1) {
      salt = salt.substring(0, saltEnd);
    }

    if (salt.length() > 8) {
      salt = salt.substring(0, 8);
    }

    return salt;
  }

  @SuppressWarnings({"FinalPrivateMethod", "FinalStaticMethod", "UnusedAssignment"})
  private static final int[] body(int[] schedule, int eSwap0, int eSwap1) {
    int left = 0;
    int right = 0;
    int t = 0;

    for (int j = 0; j < 25; j++) {
      for (int i = 0; i < ITERATIONS * 2; i += 4) {
        left = dEncrypt(left, right, i, eSwap0, eSwap1, schedule);
        right = dEncrypt(right, left, i + 2, eSwap0, eSwap1, schedule);
      }
      t = left;
      left = right;
      right = t;
    }

    t = right;

    right = (left >>> 1) | (left << 31);
    left = (t >>> 1) | (t << 31);

    left &= 0xffffffff;
    right &= 0xffffffff;

    final int[] results = new int[2];

    permOp(right, left, 1, 0x55555555, results);
    right = results[0];
    left = results[1];

    permOp(left, right, 8, 0x00ff00ff, results);
    left = results[0];
    right = results[1];

    permOp(right, left, 2, 0x33333333, results);
    right = results[0];
    left = results[1];

    permOp(left, right, 16, 0x0000ffff, results);
    left = results[0];
    right = results[1];

    permOp(right, left, 4, 0x0f0f0f0f, results);
    right = results[0];
    left = results[1];

    final int[] out = new int[2];

    out[0] = left;
    out[1] = right;

    return out;
  }

  @SuppressWarnings({"FinalPrivateMethod", "FinalStaticMethod"})
  private static final int byteToUnsigned(byte b) {
    final int value = (int)b;

    return value >= 0 ? value : value + 256;
  }

  private static String cryptTo64(int value, int length) {
    final StringBuffer output = new StringBuffer();

    while (--length >= 0) {
      output.append(I_TO_A64.substring(value & 0x3f, (value & 0x3f) + 1));
      value >>= 6;
    }

    return output.toString();
  }

  @SuppressWarnings({"FinalPrivateMethod", "FinalStaticMethod"})
  private static final int dEncrypt(int l,
    int r,
    int si,
    int e0,
    int e1,
    int[] s) {
    int t;
    int u;
    int v;

    v = r ^ (r >>> 16);
    u = v & e0;
    v = v & e1;
    u = (u ^ (u << 16)) ^ r ^ s[si];
    t = (v ^ (v << 16)) ^ r ^ s[si + 1];
    t = (t >>> 4) | (t << 28);

    l ^= SP_TRANS[1][t & 0x3f]
      | SP_TRANS[3][(t >>> 8) & 0x3f]
      | SP_TRANS[5][(t >>> 16) & 0x3f]
      | SP_TRANS[7][(t >>> 24) & 0x3f]
      | SP_TRANS[0][u & 0x3f]
      | SP_TRANS[2][(u >>> 8) & 0x3f]
      | SP_TRANS[4][(u >>> 16) & 0x3f]
      | SP_TRANS[6][(u >>> 24) & 0x3f];

    return l;
  }

  @SuppressWarnings({"PointlessBitwiseExpression"})
  private static int[] desSetKey(byte[] key) {
    final int[] schedule = new int[ITERATIONS * 2];

    int c = fourBytesToInt(key, 0);
    int d = fourBytesToInt(key, 4);

    final int[] results = new int[2];

    permOp(d, c, 4, 0x0f0f0f0f, results);
    d = results[0];
    c = results[1];

    c = hPermOp(c, -2, 0xcccc0000);
    d = hPermOp(d, -2, 0xcccc0000);

    permOp(d, c, 1, 0x55555555, results);
    d = results[0];
    c = results[1];

    permOp(c, d, 8, 0x00ff00ff, results);
    c = results[0];
    d = results[1];

    permOp(d, c, 1, 0x55555555, results);
    d = results[0];
    c = results[1];

    d = ((d & 0x000000ff) << 16) | (d & 0x0000ff00)
      | ((d & 0x00ff0000) >>> 16) | ((c & 0xf0000000) >>> 4);
    c &= 0x0fffffff;

    int s;
    int t;
    int j = 0;

    for (int i = 0; i < ITERATIONS; i++) {
      if (SHIFTS2[i]) {
        c = (c >>> 2) | (c << 26);
        d = (d >>> 2) | (d << 26);
      } else {
        c = (c >>> 1) | (c << 27);
        d = (d >>> 1) | (d << 27);
      }

      c &= 0x0fffffff;
      d &= 0x0fffffff;

      s = SKB[0][c & 0x3f]
        | SKB[1][((c >>> 6) & 0x03) | ((c >>> 7) & 0x3c)]
        | SKB[2][((c >>> 13) & 0x0f) | ((c >>> 14) & 0x30)]
        | SKB[3][((c >>> 20) & 0x01) | ((c >>> 21) & 0x06)
        | ((c >>> 22) & 0x38)];

      t = SKB[4][d & 0x3f]
        | SKB[5][((d >>> 7) & 0x03) | ((d >>> 8) & 0x3c)]
        | SKB[6][(d >>> 15) & 0x3f]
        | SKB[7][((d >>> 21) & 0x0f) | ((d >>> 22) & 0x30)];

      schedule[j++] = ((t << 16) | (s & 0x0000ffff)) & 0xffffffff;
      s = (s >>> 16) | (t & 0xffff0000);

      s = (s << 4) | (s >>> 28);
      schedule[j++] = s & 0xffffffff;
    }
    return schedule;
  }

  private static int fourBytesToInt(byte[] b, int offset) {
    int value;

    value = byteToUnsigned(b[offset++]);
    value |= byteToUnsigned(b[offset++]) << 8;
    value |= byteToUnsigned(b[offset++]) << 16;
    value |= byteToUnsigned(b[offset]) << 24;

    return value;
  }

  private static String generateNewSalt(int size) {
    final java.util.Random randomGenerator = new java.util.Random();
    final char[] salt = new char[size];
    for (int x = 0; x < salt.length; x++) {
      salt[x] = I_TO_A64.charAt(randomGenerator.nextInt(64));
    }
    return String.valueOf(salt);
  }

  @SuppressWarnings({"FinalPrivateMethod", "FinalStaticMethod"})
  private static final int hPermOp(int a, int n, int m) {
    int t;

    t = ((a << (16 - n)) ^ a) & m;
    a = a ^ t ^ (t >>> (16 - n));

    return a;
  }

  @SuppressWarnings({"FinalPrivateMethod", "FinalStaticMethod"})
  private static final void intToFourBytes(int iValue, byte[] b, int offset) {
    b[offset++] = (byte)(iValue & 0xff);
    b[offset++] = (byte)((iValue >>> 8) & 0xff);
    b[offset++] = (byte)((iValue >>> 16) & 0xff);
    b[offset] = (byte)((iValue >>> 24) & 0xff);
  }

  @SuppressWarnings({"FinalPrivateMethod", "FinalStaticMethod"})
  private static final void permOp(int a, int b, int n, int m, int[] results) {
    int t;

    t = ((a >>> n) ^ b) & m;
    a ^= t << n;
    b ^= t;

    results[0] = a;
    results[1] = b;
  }
}
