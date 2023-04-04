import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Paths;

public class Emu8086 {
    private Decoder decoder;

    private DecodeState decodeState;
    private ByteBuffer registerFile;
    private int flags;
    private ByteBuffer memory;

    public Emu8086() throws IOException {
        try (var s = Emu8086.class.getResourceAsStream("8086.txt")) {
            decoder = Decoder.decoder(s);
        }
        decodeState = new DecodeState();
        registerFile = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN);
        memory = ByteBuffer.allocate(64 * 1024).order(ByteOrder.LITTLE_ENDIAN);
    }

    private void decode(InputStream in) throws IOException {
        decoder.decode(decodeState, in);

        switch (decodeState.opcode) {
            case MOV -> mov();
            case PUSH -> push();
            case POP -> pop();
            case ADD -> add();
            case SUB -> sub();
            case CMP -> cmp();
            case JE, JNE,  JL,  JNL, JLE, JNLE, JB, JNB, JBE, JNBE, JP, JNP, JO, JNO, JS, JNS, LOOP, LOOPZ, LOOPNZ, JCXZ -> {
                jump();
            }
            default -> System.out.printf("%s not implemented%n", decodeState.opcode);
        }
    }

    private void setFlag(Flag flag, boolean value) {
        if (value) {
            setFlag(flag);
        } else {
            clearFlag(flag);
        }
    }

    private void setFlag(Flag flag) {
        flags |= flag.setTestMask;
    }

    private void clearFlag(Flag flag) {
        flags &= flag.clearMask;
    }

    private boolean getFlag(Flag flag) {
        return (flags & flag.setTestMask) != 0;
    }
    
    private void cmp() {
        printTwoOperand();
    }

    private void sub() {
        printTwoOperand();
    }

    private void add() {
        Register reg = decodeState.reg;
        Address rm = decodeState.rm;

        ByteBuffer previousRegisterFile = ByteBuffer.allocate(registerFile.capacity()).order(registerFile.order());
        previousRegisterFile.put(registerFile);
        previousRegisterFile.clear();
        registerFile.clear();

        Address target;
        int op1;
        int op2;
        if (rm == null) {
            op1 = get(reg);
            op2 = decodeState.immediate;
            target = reg;
        } else if (reg == null) {
            op1 = get(rm);
            op2 = decodeState.immediate;
            target = rm;
        } else if (decodeState.isToReg) {
            op1 = get(reg);
            op2 = get(rm);
            target = reg;
        } else {
            op1 = get(reg);
            op2 = get(rm);
            target = rm;
        }

        int result = op1 + op2;

        setFlag(Flag.CARRY, decodeState.isWide ? ((result & 0x100) != 0) : ((result & 0x10) != 0));
        setFlag(Flag.SIGN, decodeState.isWide ? ((result & 0x80) != 0) : ((result & 0x8) != 0));
        setFlag(Flag.ZERO, result == 0);
        setFlag(Flag.PARITY, ParityTable.getParity(result));

        set(target, result);

        printTwoOperand();
        printRegisterChange(previousRegisterFile);
    }

    private void pop() {
        printOneOperand();
    }

    private void push() {
        printOneOperand();
    }

    private void mov() {
        Register reg = decodeState.reg;
        Address rm = decodeState.rm;

        ByteBuffer previousRegisterFile = ByteBuffer.allocate(registerFile.capacity()).order(registerFile.order());
        previousRegisterFile.put(registerFile);
        previousRegisterFile.clear();
        registerFile.clear();

        if (rm == null) {
            set(reg, decodeState.immediate);
        } else if (reg == null) {
            set(rm, decodeState.immediate);
        } else if (decodeState.isToReg) {
            set(reg, get(rm));
        } else {
            set(rm, get(reg));
        }

        printTwoOperand();
        printRegisterChange(previousRegisterFile);
    }

    private void jump() {
        System.out.printf("%s %s%n", decodeState.opcode, decodeState.immediate);
    }

    private int get(Address address) {
        if (address instanceof Register r) {
            return get(r);
        } else {
            return get(((MemoryAddress) address));
        }
    }

    private int get(Register register) {
        return get(register, registerFile);
    }

    private static int get(Register register, ByteBuffer registerFile) {
        int o = register.offset;
        if (o < 0) {
            return registerFile.get(-o - 1) & 0xFF;
        } else {
            return registerFile.getChar(o);
        }
    }

    private int get(MemoryAddress address) {
        if (decodeState.isWide) {
            return memory.getChar(effectiveAddress(address));
        } else {
            return memory.get(effectiveAddress(address)) & 0xFF;
        }
    }

    private void set(Address address, int value) {
        if (address instanceof Register r) {
            set(r, value);
        } else {
            set(((MemoryAddress) address), value);
        }
    }

    private void set(Register register, int value) {
        int o = register.offset;
        if (o < 0) {
            registerFile.put(-o - 1, (byte) value);
        } else {
            registerFile.putChar(o, (char) value);
        }
    }

    private void set(MemoryAddress address, int value) {
        if (decodeState.isWide) {
            memory.putChar(effectiveAddress(address), (char)value);
        } else {
            memory.put(effectiveAddress(address), (byte)value);
        }
    }

    private int effectiveAddress(MemoryAddress address) {
        int base = switch (address.mode()) {
            case BX_SI -> get(Register.BX) + get(Register.SI);
            case BX_DI -> get(Register.BX) + get(Register.DI);
            case BP_SI -> get(Register.BP) + get(Register.SI);
            case BP_DI -> get(Register.BP) + get(Register.SI);
            case SI -> get(Register.SI);
            case DI -> get(Register.DI);
            case BP -> get(Register.BP);
            case BX -> get(Register.BX);
            case DIRECT -> 0;
        };
        int ea = base + address.displacement();
        return ea;
    }
    private void printOneOperand() {
        Address op;
        if (decodeState.reg == null) {
            op = decodeState.rm;
        } else {
            op = decodeState.reg;
        }

        System.out.printf("%s %s%n", decodeState.opcode, op);
    }

    private void printTwoOperand() {
        Address rm = decodeState.rm;
        Address reg = decodeState.reg;

        if (rm == null) {
            System.out.printf("%s %s, %d%n", decodeState.opcode, reg, decodeState.immediate);
        } else if (reg == null) {
            System.out.printf("%s %s, %s%d%n", decodeState.opcode, rm, ((rm instanceof MemoryAddress) ? (decodeState.isWide ? "word " : "byte ") : ""), decodeState.immediate);
        } else if (decodeState.isToReg) {
            System.out.printf("%s %s, %s%n", decodeState.opcode, reg, rm);
        } else {
            System.out.printf("%s %s, %s%n", decodeState.opcode, rm, reg);
        }
    }

    private void printRegisterFile() {
        for (Register register : Register.values()) {
            if (register.offset < 0) {
                continue;
            }
            System.out.printf("%s: 0x%04x%n", register, get(register));
        }
        StringBuilder flags = new StringBuilder();
        for (Flag f : Flag.values()) {
            if (getFlag(f)) {
                flags.append(f.name().substring(0, 1));
            }
        }
        System.out.printf("%s: %s%n", "flags", flags);
    }

    private void printRegisterChange(ByteBuffer previousRegisterFile) {
        for (Register register : Register.values()) {
            if (register.offset < 0) {
                continue;
            }

            int previous = get(register, previousRegisterFile);
            int current = get(register);
            if (previous != current) {
                System.out.printf("%s: 0x%04x -> 0x%04x%n", register, previous, current);
            }
        }
    }

    public static void main(String[] args) throws IOException {
        Emu8086 cpu = new Emu8086();
        try (InputStream in = Files.newInputStream(Paths.get(args[0]))) {
            while (true) {
                cpu.decode(in);
            }
        } catch (EOFException e) {
            // done
        }
        cpu.printRegisterFile();
    }
}