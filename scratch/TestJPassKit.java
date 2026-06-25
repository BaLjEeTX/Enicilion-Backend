import de.brendamour.jpasskit.PKPass;
import de.brendamour.jpasskit.passes.PKEventTicket;
public class TestJPassKit {
    public static void main(String[] args) {
        PKPass.builder().pass(PKEventTicket.builder().build());
    }
}
