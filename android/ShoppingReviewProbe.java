import ai.eqo.core.agent.*;
import ai.eqo.accessibility.*;
import java.util.*;

public class ShoppingReviewProbe {
    static TaskIntent generic() {
        return new TaskIntent("Model task", null, "other", Map.of(), null, "unknown", 1.0, null, List.of(), null);
    }
    static FakeNode button(String text) {
        return new FakeNode(text, null, null, true, false, false, false, false, null, "android.widget.Button", null);
    }
    public static void main(String[] args) throws Exception {
        for (String id : List.of("shop:id/cod_button", "shop:id/select_cod", "shop:id/place_order")) {
            var step = new LoopStep("task-1", new ExecutedAction("CLICK_ID", Map.of("viewId", id), true, List.of(), ""), null);
            try {
                ShoppingPlans.INSTANCE.validate(generic(), List.of(step));
                System.out.println("payment target ACCEPTED: " + id);
            } catch (IllegalArgumentException e) {
                System.out.println("payment target REFUSED: " + id);
            }
        }
        var firstOtherPlus = button("+");
        var milkAdd = button("Add");
        var milkPlus = button("+");
        var root = new FakeNode().child(new FakeNode("bread", null, null, false, false, false, false, false, null, "row", null).child(firstOtherPlus))
            .child(new FakeNode("milk", null, null, false, false, false, false, false, null, "row", null).child(milkAdd).child(milkPlus));
        var runner = new EqoAutomation(() -> root, () -> EqoAutomation.ServiceState.AVAILABLE, new TakeoverDetector(), () -> false, null);
        var method = EqoAutomation.class.getDeclaredMethod("bestTappable", A11yNode.class, String.class, boolean.class);
        method.setAccessible(true);
        ((A11yNode)method.invoke(runner, root, "exact:Add,exact:ADD,exact:Add to cart", false)).click();
        ((A11yNode)method.invoke(runner, root, "exact:+,exact:Increase quantity", false)).click();
        System.out.println("quantity selection: milk Add=" + milkAdd.getClickCount() + ", bread +=" + firstOtherPlus.getClickCount() + ", milk +=" + milkPlus.getClickCount());
    }
}
