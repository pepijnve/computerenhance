public record MemoryAddress(EffectiveAddress mode, int displacement) implements Address {
    public String toString() {
        int disp = displacement;
        if (mode == EffectiveAddress.DIRECT) {
            return "[" + disp + "]";
        } else {
            return "[" + mode + (disp != 0 ? ((disp < 0 ? " - " : " + ") + Math.abs(disp)) : "") + "]";
        }
    }
}
