import java.util.EnumSet;

public record Op(Opcode code, EnumSet<OpcodeFlags> flags) {
}
