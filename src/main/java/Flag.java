public enum Flag {
    CARRY(0),
    PARITY(2),
    AUXILIARY_CARRY(4),
    ZERO(6),
    SIGN(7),
    OVERFLOW(8),
    INTERRUPT_ENABLE(9),
    DIRECTION(10),
    TRAP(11),
    ;

    public final int bitIndex;
    public final int setTestMask;
    public final int clearMask;

    Flag(int bitIndex) {
        this.bitIndex = bitIndex;
        setTestMask = 1 << bitIndex;
        clearMask = ~setTestMask;
    }
}
