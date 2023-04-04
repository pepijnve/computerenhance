import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;

public class Decoder {
    private Register[] SEGMENT_REGISTER_TABLE = new Register[]{
            Register.ES,
            Register.CS,
            Register.SS,
            Register.DS
    };

    private Register[] BYTE_REGISTER_TABLE = new Register[]{
            Register.AL,
            Register.CL,
            Register.DL,
            Register.BL,
            Register.AH,
            Register.CH,
            Register.DH,
            Register.BH
    };

    private Register[] WORD_REGISTER_TABLE = new Register[]{
            Register.AX,
            Register.CX,
            Register.DX,
            Register.BX,
            Register.SP,
            Register.BP,
            Register.SI,
            Register.DI
    };

    private EffectiveAddress[] MEM_TABLE = new EffectiveAddress[]{
            EffectiveAddress.BX_SI,
            EffectiveAddress.BX_DI,
            EffectiveAddress.BP_SI,
            EffectiveAddress.BP_DI,
            EffectiveAddress.SI,
            EffectiveAddress.DI,
            EffectiveAddress.BP,
            EffectiveAddress.BX
    };

    private OpcodeMatcher[] opcodeMatchers = new OpcodeMatcher[256];
    private ModRegRmMatcher[][] modRegRmTable = new ModRegRmMatcher[256][];

    private Decoder() {
    }

    public static Decoder decoder(InputStream instructionTable) throws IOException {
        Decoder d = new Decoder();

        BufferedReader r = new BufferedReader(new InputStreamReader(instructionTable, StandardCharsets.UTF_8));
        String line;
        Opcode opcode = null;
        while ((line = r.readLine()) != null) {
            line = line.strip();
            if (line.isEmpty()) {
                continue;
            }

            if (line.startsWith("#")) {
                continue;
            }

            if (line.startsWith("=")) {
                opcode = Opcode.valueOf(line.substring(1).strip().toUpperCase());
                continue;
            }

            line = line.replaceAll("\\s+", "");

            String opcodeByte = line.substring(0, 8);
            line = line.substring(8);

            String modRegRmByte;
            if (line.startsWith("md")) {
                modRegRmByte = line.substring(0, 8);
                line = line.substring(8);
            } else {
                modRegRmByte = null;
            }

            String flagsString = line.toUpperCase();
            EnumSet<OpcodeFlags> flags = EnumSet.noneOf(OpcodeFlags.class);
            for (OpcodeFlags flag : OpcodeFlags.values()) {
                if (flagsString.contains(flag.name())) {
                    flags.add(flag);
                    flagsString = flagsString.replaceAll(flag.name(), "");
                }
            }
            if (!flagsString.isEmpty()) {
                throw new IOException("Unhandled flags: " + line);
            }

            Op op = new Op(opcode, flags);

            opcodeByte = opcodeByte.replace("reg", "rrr");
            opcodeByte = opcodeByte.replace("sr", "gg");
            // Special case hack for ESC: store xxx as reg
            opcodeByte = opcodeByte.replace("xxx", "rrr");

            if (modRegRmByte != null) {
                modRegRmByte = modRegRmByte.replace("md", "mm");
                modRegRmByte = modRegRmByte.replace("r/m", "nnn");
                modRegRmByte = modRegRmByte.replace("reg", "rrr");
                modRegRmByte = modRegRmByte.replace("sr", "gg");
                // Special case hack for ESC: store yyy as seg
                modRegRmByte = modRegRmByte.replace("yyy", "ggg");
            }

            int opcodeMask = 0;
            int opcodeValue = 0;
            int dMask = 0;
            int wMask = 0;
            int sMask = 0;
            int vMask = 0;
            int zMask = 0;

            int segMask = 0;
            int segShift = 0;

            int regMask = 0;
            int regShift = 0;

            for (int i = 0, bit = 0x80; i < opcodeByte.length(); i++, bit >>= 1) {
                char c = opcodeByte.charAt(i);
                if (c == '0') {
                    opcodeMask |= bit;
                } else if (c == '1') {
                    opcodeMask |= bit;
                    opcodeValue |= bit;
                } else if (c == 'w') {
                    wMask = bit;
                } else if (c == 'd') {
                    dMask = bit;
                } else if (c == 's') {
                    sMask = bit;
                } else if (c == 'v') {
                    vMask = bit;
                } else if (c == 'z') {
                    zMask = bit;
                } else if (c == 'g') {
                    segMask |= bit;
                    segShift = 7 - i;
                } else if (c == 'r') {
                    regMask |= bit;
                    regShift = 7 - i;
                }
            }

            OpcodeMatcher opcodeMatcher = new OpcodeMatcher(
                    opcodeMask,
                    opcodeValue,
                    dMask,
                    wMask,
                    sMask,
                    vMask,
                    zMask,
                    segMask,
                    segShift,
                    regMask,
                    regShift,
                    modRegRmByte == null ? op : null
            );

            ModRegRmMatcher modRegRmMatcher = null;
            if (modRegRmByte != null) {
                int modRegRmMask = 0;
                int modRegRmValue = 0;

                int modMask = 0;
                int modShift = 0;

                int rmMask = 0;
                int rmShift = 0;

                for (int i = 0, bit = 0x80; i < modRegRmByte.length(); i++, bit >>= 1) {
                    char c = modRegRmByte.charAt(i);
                    if (c == '0') {
                        modRegRmMask |= bit;
                    } else if (c == '1') {
                        modRegRmMask |= bit;
                        modRegRmValue |= bit;
                    } else if (c == 'm') {
                        modMask |= bit;
                        modShift = 7 - i;
                    } else if (c == 'n') {
                        rmMask |= bit;
                        rmShift = 7 - i;
                    } else if (c == 'g') {
                        segMask |= bit;
                        segShift = 7 - i;
                    } else if (c == 'r') {
                        regMask |= bit;
                        regShift = 7 - i;
                    }
                }

                modRegRmMatcher = new ModRegRmMatcher(
                        modRegRmMask, modRegRmValue,
                        modMask, modShift,
                        rmMask, rmShift,
                        regMask, regShift,
                        segMask, segShift,
                        op
                );
            }

            for (int i = 0; i < d.opcodeMatchers.length; i++) {
                if (opcodeMatcher.matches(i)) {
                    d.opcodeMatchers[i] = opcodeMatcher;
                    if (modRegRmMatcher != null) {
                        if (d.modRegRmTable[i] == null) {
                            d.modRegRmTable[i] = new ModRegRmMatcher[8];
                        }
                        for (int j = 0; j < 8; j++) {
                            if (modRegRmMatcher.matchesReg(j)) {
                                d.modRegRmTable[i][j] = modRegRmMatcher;
                            }
                        }
                    }
                }
            }
        }

        return d;
    }

    public void decode(DecodeState decodeState, InputStream in) throws IOException {
        int byte1 = readU8(in);
        OpcodeMatcher o = opcodeMatchers[byte1];
        if (o == null) {
            throw new IOException("Illegal instruction: " + Integer.toBinaryString(byte1));
        }

        Op op = o.op();
        boolean isToReg = o.dMask != 0 ? (byte1 & o.dMask) != 0 : o.regMask != 0;
        boolean isWide = (byte1 & o.wMask) != 0;
        boolean isSignExtend = (byte1 & o.sMask) != 0;
        boolean isRotateCL = (byte1 & o.vMask) != 0;
        boolean isWhileZero = (byte1 & o.zMask) != 0;

        Register reg = null;

        if (o.regMask != 0) {
            int regValue = (byte1 & o.regMask) >> o.regShift;
            reg = isWide ? WORD_REGISTER_TABLE[regValue] : BYTE_REGISTER_TABLE[regValue];
        } else if (o.segMask != 0) {
            reg = SEGMENT_REGISTER_TABLE[(byte1 & o.segMask) >> o.segShift];
            isWide = true;
        }

        Address rmAddr = null;
        if (op == null) {
            int byte2 = readU8(in);
            ModRegRmMatcher[] modRegRmMatchers = modRegRmTable[byte1];
            ModRegRmMatcher m = modRegRmMatchers == null ? null : modRegRmMatchers[(byte2 >> 3) & 0x7];
            if (m == null) {
                throw new IOException("Illegal instruction: " + Integer.toHexString(byte1) + " " + Integer.toHexString(byte2));
            }

            op = m.op();
            int mod = (byte2 & m.modMask) >> m.modShift;
            int rm = (byte2 & m.rmMask) >> m.rmShift;

            if (m.regMask != 0) {
                int regValue = (byte2 & m.regMask) >> m.regShift;
                reg = isWide ? WORD_REGISTER_TABLE[regValue] : BYTE_REGISTER_TABLE[regValue];
            } else if (m.segMask != 0) {
                reg = SEGMENT_REGISTER_TABLE[(byte2 & m.segMask) >> m.segShift];
                isWide = true;
            }

            switch (mod) {
                case 0b00 -> {
                    if (rm == 0b110) {
                        rmAddr = new MemoryAddress(EffectiveAddress.DIRECT, readU16(in));
                    } else {
                        rmAddr = new MemoryAddress(MEM_TABLE[rm], 0);
                    }
                }
                case 0b01 -> {
                    rmAddr = new MemoryAddress(MEM_TABLE[rm], readS8(in));
                }
                case 0b10 -> {
                    rmAddr = new MemoryAddress(MEM_TABLE[rm], readU16(in));
                }
                case 0b11 -> {
                    rmAddr = isWide ? WORD_REGISTER_TABLE[rm] : BYTE_REGISTER_TABLE[rm];
                }
            }
            ;
        }

        int immediate = 0;
        EnumSet<OpcodeFlags> flags = op.flags();
        for (OpcodeFlags flag : flags) {
            switch (flag) {
                case ADDRW -> {
                    rmAddr = new MemoryAddress(EffectiveAddress.DIRECT, isWide ? readU16(in) : readU8(in));
                }
                case DATAW -> immediate = isWide ? readU16(in) : readS8(in);
                case DATAS -> immediate = isWide ? (isSignExtend ? readS8(in) : readU16(in)) : readU8(in);
                case UINT8 -> immediate = readU8(in);
                case UINT16 -> immediate = readU16(in);
                case SINT8 -> immediate = readS8(in);
                case SINT16 -> immediate = readS16(in);
                case TO_ACC -> {
                    if (reg == null) {
                        reg = isWide ? Register.AX : Register.AL;
                        isToReg = true;
                    } else {
                        rmAddr = isWide ? Register.AX : Register.AL;
                        isToReg = false;
                    }
                }
                case FROM_ACC -> {
                    if (reg == null) {
                        reg = isWide ? Register.AX : Register.AL;
                        isToReg = false;
                    } else {
                        rmAddr = isWide ? Register.AX : Register.AL;
                        isToReg = true;
                    }
                }
                case TO_SR -> {
                    isToReg = true;
                }
                case FROM_SR -> {
                    isToReg = false;
                }
            }
        }

        decodeState.opcode = op.code();
        decodeState.isToReg = isToReg;
        decodeState.isWide = isWide;
        decodeState.isRotateCL = isRotateCL;
        decodeState.isWhileZero = isWhileZero;

        decodeState.reg = reg;
        decodeState.rm = rmAddr;
        decodeState.immediate = immediate;
    }

    private static int readS8(InputStream in) throws IOException {
        return (byte) readU8(in);
    }

    private static int readS16(InputStream in) throws IOException {
        return (short) readU16(in);
    }

    private static int readU8(InputStream in) throws IOException {
        int b = in.read();
        if (b < 0) {
            throw new EOFException();
        }
        return b;
    }

    private static int readU16(InputStream in) throws IOException {
        return readU8(in) | (readU8(in) << 8);
    }

    private record OpcodeMatcher(
            int fixedMask, int fixedValue,
            int dMask,
            int wMask,
            int sMask,
            int vMask,
            int zMask,
            int segMask,
            int segShift,
            int regMask,
            int regShift,
            Op op
    ) {
        public boolean matches(int byteValue) {
            return (byteValue & fixedMask) == fixedValue;
        }

        public String toString() {
            StringBuilder b = new StringBuilder();
            for (int i = 7; i >= 0; i--) {
                int mask = 1 << i;
                if ((fixedMask & mask) == 0) {
                    b.append("*");
                } else {
                    if ((fixedValue & mask) == 0) {
                        b.append("0");
                    } else {
                        b.append("1");
                    }
                }
            }
            return b.toString();
        }
    }

    private record ModRegRmMatcher(
            int fixedMask, int fixedValue,
            int modMask, int modShift,
            int rmMask, int rmShift,
            int regMask, int regShift,
            int segMask, int segShift,
            Op op) {
        public boolean matches(int byteValue) {
            return (byteValue & fixedMask) == fixedValue;
        }

        public String toString() {
            StringBuilder b = new StringBuilder();
            for (int i = 7; i >= 0; i--) {
                int mask = 1 << i;
                if ((fixedMask & mask) == 0) {
                    b.append("*");
                } else {
                    if ((fixedValue & mask) == 0) {
                        b.append("0");
                    } else {
                        b.append("1");
                    }
                }
            }
            return b.toString();
        }

        public boolean matchesReg(int reg) {
            int shiftedReg = reg << 3;
            return (shiftedReg & fixedMask) == fixedValue;
        }
    }

}
