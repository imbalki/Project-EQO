// Origin: EQO synthetic close-only shopping popup and product-binding regressions.
package ai.eqo.accessibility

import ai.eqo.core.agent.ShoppingPlans
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShoppingPopupCloseTest {
    @Test fun onlyExactClickableCloseOrObservedIdCanBeTapped() {
        val labels = listOf("Close", "cLoSe", "Dismiss", "Not now", "Skip", "x")
        for (label in labels) {
            for (description in listOf(true, false)) {
                val node =
                    FakeNode(
                        text = if (description) null else label,
                        contentDescription = if (description) label else null,
                        isClickable = true,
                    )
                assertTrue(automation(node).dismissShoppingPopup().isSuccess)
                assertEquals(1, node.clickCount)
            }
        }
        val close = FakeNode(viewIdResourceName = "com.zeptoconsumerapp:id/bottomSheetClose", isClickable = true)
        assertTrue(automation(close).dismissShoppingPopup().isSuccess)
        assertEquals(1, close.clickCount)
    }

    @Test fun neverTapCommerceLabelsEvenWhenCloseIdOrAlternateCloseLabelMatches() {
        val labels = listOf("Order Now", "Pay", "Buy", "Checkout", "Place order", "Add address", "Apply", "UPI")
        for (label in labels + listOf("Apply coupon", "Buy item")) {
            for (byId in listOf(true, false)) {
                val node =
                    FakeNode(
                        text = label,
                        contentDescription = if (byId) null else "Close",
                        viewIdResourceName = if (byId) "bottomSheetClose" else null,
                        isClickable = true,
                    )
                assertFalse(automation(node).dismissShoppingPopup().isSuccess)
                assertEquals(0, node.clickCount)
            }
            val ordinary = FakeNode(text = label, isClickable = true)
            assertFalse(automation(ordinary).dismissShoppingPopup().isSuccess)
            assertEquals(0, ordinary.clickCount)
        }
    }

    @Test fun noAncestorClickFallbackPartialMatchOrUnsafeSubtree() {
        val nodes =
            listOf(
                FakeNode(isClickable = true).child(FakeNode(text = "Close")),
                FakeNode(text = "Close and pay", isClickable = true),
                FakeNode(text = "Skip offer", isClickable = true),
                FakeNode(text = "Close", isEditable = true, isClickable = true),
                FakeNode(text = "Close", isPassword = true, isClickable = true),
                FakeNode(text = "Pay").child(FakeNode(text = "Close", isClickable = true)),
                FakeNode(viewIdResourceName = "bottomSheetClose", isClickable = true).child(FakeNode(text = "Pay")),
            )
        for (node in nodes) {
            assertFalse(automation(node).dismissShoppingPopup().isSuccess)
            assertEquals(0, node.clickCount)
            assertEquals(0, (node.childAt(0) as? FakeNode)?.clickCount ?: 0)
        }
    }

    @Test fun dismissalPreservesProductBindingForQuantityRetry() {
        val add = FakeNode(text = "Add", isClickable = true)
        val plus = FakeNode(contentDescription = "increase quantity", isClickable = true)
        val product = FakeNode().child(FakeNode().child(FakeNode(text = "Milk")).child(add).child(plus))
        var root = product
        val runner = EqoAutomation({ root }, { EqoAutomation.ServiceState.AVAILABLE }, TakeoverDetector())
        assertTrue(runner.tap("exact:Add,exact:ADD,exact:Add to cart").isSuccess)
        val order = FakeNode(text = "Order Now", isClickable = true)
        val close = FakeNode(viewIdResourceName = "bottomSheetClose", isClickable = true)
        close.onAction = { root = product }
        root = FakeNode().child(order).child(close)
        assertFalse(runner.tap(ShoppingPlans.INCREASE).isSuccess)
        assertTrue(runner.dismissShoppingPopup().isSuccess)
        assertTrue(runner.tap(ShoppingPlans.INCREASE).isSuccess)
        assertEquals(listOf(1, 1, 1, 0), listOf(add, close, plus, order).map { it.clickCount })
    }

    @Test fun gatesBlockCloseWithoutTouchingNode() {
        val close = FakeNode(text = "Close", isClickable = true)
        val takeover = TakeoverDetector()
        val secure = EqoAutomation({ close }, { EqoAutomation.ServiceState.AVAILABLE }, takeover, { true })
        assertFalse(secure.dismissShoppingPopup().isSuccess)
        val disabled = EqoAutomation({ close }, { EqoAutomation.ServiceState.ACCESSIBILITY_DISABLED }, takeover)
        assertFalse(disabled.dismissShoppingPopup().isSuccess)
        val ownRoot = FakeNode(packageName = "eqo").child(close)
        val ownWindow =
            EqoAutomation({ ownRoot }, { EqoAutomation.ServiceState.AVAILABLE }, takeover, ownPackage = "eqo")
        assertFalse(ownWindow.dismissShoppingPopup().isSuccess)
        val userTakeover = TakeoverDetector()
        userTakeover.onAgentActionStarted()
        userTakeover.onTouch(TakeoverDetector.TouchSource.USER)
        userTakeover.onAgentActionFinished()
        val paused = EqoAutomation({ close }, { EqoAutomation.ServiceState.AVAILABLE }, userTakeover)
        assertFalse(paused.dismissShoppingPopup().isSuccess)
        assertEquals(0, close.clickCount)
    }

    private fun automation(root: FakeNode): EqoAutomation =
        EqoAutomation({ root }, { EqoAutomation.ServiceState.AVAILABLE }, TakeoverDetector())
}
