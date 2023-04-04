public class ParityTable {
    private static boolean[] PARITY = new boolean[256];

    static {
        for (int i = 0; i < PARITY.length; i++) {
            PARITY[i] = parity(i);
        }
    }

    private static boolean parity(int i) {
        boolean parity = true;
        for (int j = 0; j < 8; j++) {
            if ((i & 1 << j) != 0) {
                parity = !parity;
            }
        }
        return parity;
    }

    public static boolean getParity(int value) {
        return PARITY[value & 0xFF];
    }
}
