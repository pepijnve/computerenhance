public enum EffectiveAddress {
    BX_SI("BX + SI"),
    BX_DI("BX + DI"),
    BP_SI("BP + SI"),
    BP_DI("BP + DI"),
    SI("SI"),
    DI("DI"),
    BP("BP"),
    BX("BX"),
    DIRECT("");

    private final String value;

    EffectiveAddress(String value) {
        this.value = value;
    }

    public String toString() {
        return value;
    }
}
