public class DecodeState {
    public Opcode opcode;
    public boolean isToReg;
    public boolean isWide;
    public boolean isRotateCL;
    public boolean isWhileZero;
    public Register reg;
    public Address rm;
    public int immediate;
}
