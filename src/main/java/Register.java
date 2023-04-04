public enum Register implements Address {
    AL(-1), AH(-2), AX(0),
    BL(-3), BH(-4), BX(2),
    CL(-5), CH(-6), CX(4),
    DL(-7), DH(-8), DX(6),
    SP(8),
    BP(10),
    SI(12),
    DI(14),
    CS(16),
    DS(18),
    SS(20),
    ES(22);

    final int offset;

    Register(int offset) {
        this.offset = offset;
    }
}
