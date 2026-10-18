// Origin: EQO synthetic regression for observed Zepto shopping controls.
package ai.eqo.accessibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShoppingObservedControlsTest {
    @Test fun stepperIdsNeverExemptPaymentLabelsAncestorsOrUnobservedIds() {
        val packageName = "com.zeptoconsumerapp"
        val addId = "$packageName:id/product-card-add-button00000000-0000-0000-0000-000000000001"
        for (suffix in listOf("-plus", "-minus")) {
            val nodes =
                listOf(
                    FakeNode(
                        text = "Add",
                        contentDescription = "Pay",
                        viewIdResourceName = addId + suffix,
                        packageName = packageName,
                        isClickable = true,
                    ),
                    FakeNode(
                        text = "Add",
                        viewIdResourceName = addId + suffix + "-pay",
                        packageName = packageName,
                        isClickable = true,
                    ),
                    FakeNode(
                        text = "Add",
                        viewIdResourceName = addId + suffix,
                        packageName = "other.shop",
                        isClickable = true,
                    ),
                    FakeNode(contentDescription = "COD").child(
                        FakeNode(
                            text = "Add",
                            viewIdResourceName = addId + suffix,
                            packageName = packageName,
                            isClickable = true,
                        ),
                    ),
                )
            for (node in nodes) {
                val runner =
                    EqoAutomation({ node }, { EqoAutomation.ServiceState.AVAILABLE }, TakeoverDetector())
                assertFalse(runner.tap("exact:Add").isSuccess)
                assertEquals(0, node.clickCount)
                assertEquals(0, (node.childAt(0) as? FakeNode)?.clickCount ?: 0)
            }
        }
    }

    @Test fun observedZeptoButtonsBindChildAddAndLowercaseEmptyTextStepper() {
        val packageName = "com.zeptoconsumerapp"
        val addId = "$packageName:id/product-card-add-button00000000-0000-0000-0000-000000000001"
        val add =
            FakeNode(
                text = "",
                contentDescription = "Add to Cart ",
                className = "android.widget.Button",
                viewIdResourceName = addId,
                packageName = packageName,
                isClickable = true,
            ).child(FakeNode(text = "ADD", className = "android.widget.TextView"))
        val minus =
            FakeNode(
                text = "",
                contentDescription = "decrease quantity",
                className = "android.widget.Button",
                viewIdResourceName = "$addId-minus",
                packageName = packageName,
                isClickable = true,
            )
        val plus =
            FakeNode(
                text = "",
                contentDescription = "increase quantity",
                className = "android.widget.Button",
                viewIdResourceName = "$addId-plus",
                packageName = packageName,
                isClickable = true,
            )
        val cart = FakeNode(text = "Cart", isClickable = true).child(FakeNode(text = "1 item"))
        var root = FakeNode().child(FakeNode().child(FakeNode(text = "Milk")).child(add)).child(cart)
        val runner = EqoAutomation({ root }, { EqoAutomation.ServiceState.AVAILABLE }, TakeoverDetector())
        val intent =
            ai.eqo.core.agent.TaskIntent(
                "Buy milk",
                "Zepto",
                "purchase",
                mapOf("item" to "milk", "quantity" to "2"),
                confidence = 1.0,
            )
        val actions =
            ai.eqo.core.agent.ShoppingPlans
                .plan(intent)
                .takeLast(3)
        assertTrue(runner.tap(actions[0].params.getValue("text")).isSuccess)
        root =
            FakeNode()
                .child(
                    FakeNode().child(FakeNode(text = "Milk")).child(
                        FakeNode().child(minus).child(FakeNode(text = "1")).child(plus),
                    ),
                ).child(cart)
        assertTrue(runner.tap(actions[1].params.getValue("text")).isSuccess)
        assertTrue(runner.tap(actions[2].params.getValue("text")).isSuccess)
        assertEquals(listOf(1, 0, 1, 1), listOf(add, minus, plus, cart).map { it.clickCount })
    }
}
