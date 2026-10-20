// Origin: EQO synthetic regression for observed Zepto shopping controls.
package ai.eqo.accessibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import javax.xml.parsers.DocumentBuilderFactory

class ShoppingObservedControlsTest {
    @Test fun capturedZeptoAttributesMatchWithoutTextOrMinimumSize() {
        val nodes = capturedNodes()
        assertEquals(4, nodes.size)
        assertTrue(NodeTreeSearch.matchesExactly(nodes[0], "decrease quantity"))
        assertTrue(NodeTreeSearch.matchesExactly(nodes[1], "increase quantity"))
        assertEquals("", nodes[1].text)
        assertEquals("...-plus", nodes[1].viewIdResourceName)
        for (add in nodes.drop(2)) {
            assertTrue(NodeTreeSearch.matchesExactly(add, "Add to cart"))
            val runner = EqoAutomation({ add }, { EqoAutomation.ServiceState.AVAILABLE }, TakeoverDetector())
            assertTrue(runner.tap("exact:Add,exact:ADD,exact:Add to cart").isSuccess)
            assertEquals(1, add.clickCount)
        }
    }

    @Test fun capturedIncreaseWithoutProductBindingIsNotAnArbitraryFallback() {
        // The capture omits ancestors. Matching attributes alone cannot establish which product was added.
        val plus = capturedNodes()[1]
        val runner = EqoAutomation({ plus }, { EqoAutomation.ServiceState.AVAILABLE }, TakeoverDetector())
        assertTrue(NodeTreeSearch.matchesExactly(plus, "increase quantity"))
        val result = runner.tap(ai.eqo.core.agent.ShoppingPlans.INCREASE)
        assertTrue(result is A11yResult.Failure && result.error is A11yError.NodeNotFound)
        assertEquals(0, plus.clickCount)
    }

    private fun capturedNodes(): List<FakeNode> {
        // Preserve the provided opening tags and abbreviated IDs verbatim in the resource.
        // Only close tags here to make a parseable document; do not invent missing parents or UUIDs.
        val tags =
            requireNotNull(javaClass.getResourceAsStream("/shopping/zepto-stepper-nodes.txt"))
                .bufferedReader()
                .useLines { lines -> lines.joinToString("") { it.removeSuffix(">") + "/>" } }
        val document =
            DocumentBuilderFactory
                .newInstance()
                .newDocumentBuilder()
                .parse(("<nodes>" + tags + "</nodes>").byteInputStream())
        val nodes = document.getElementsByTagName("node")
        return (0 until nodes.length).map { index ->
            val node = nodes.item(index) as Element
            FakeNode(
                text = node.getAttribute("text"),
                contentDescription = node.getAttribute("content-desc"),
                viewIdResourceName = node.getAttribute("resource-id"),
                className = node.getAttribute("class"),
                packageName = node.getAttribute("package"),
                isClickable = node.getAttribute("clickable").toBoolean(),
                isPassword = node.getAttribute("password").toBoolean(),
            )
        }
    }

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

    @Test fun realZeptoProductCardWithManyLabelsBindsAndReachesCapturedStepper() {
        // Failing-first reproducer for build 21d98e4 (real captured attributes, w-shopfix3 dumps):
        // the card carries a summary node, product name and pack size, so the old single-label
        // capture never bound, and the step Increase reported NodeNotFound until the run failed.
        val summary = "₹40, Milky Mist Lactose Free Milk, 1 pack (250 ml), ₹\u200a34"
        val neighbourSummary = "₹42, Nandini Good Life Milk, 500 ml"
        var root =
            FakeNode()
                .child(FakeNode().child(card(firstUuid, summary)))
                .child(FakeNode().child(card(neighbourUuid, neighbourSummary)))
        val runner = EqoAutomation({ root }, { EqoAutomation.ServiceState.AVAILABLE }, TakeoverDetector())
        assertTrue(runner.tap("exact:Add,exact:ADD,exact:Add to cart").isSuccess)
        val minus = stepper("${slotId(firstUuid)}-minus")
        val plus = stepper("${slotId(firstUuid)}-plus")
        root =
            FakeNode()
                .child(
                    FakeNode().child(stepperCard(firstUuid, summary, "1 item", minus, plus)),
                ).child(FakeNode().child(card(neighbourUuid, neighbourSummary)))
        val result = runner.tap(ai.eqo.core.agent.ShoppingPlans.INCREASE)
        assertTrue("expected success, got $result", result.isSuccess)
        assertEquals(1, plus.clickCount)
        assertEquals(0, minus.clickCount)
        // The neighbouring product's Add must stay untouched.
        assertEquals(0, neighbourAddClicks(root))
    }

    @Test fun unrelatedClickableInsideBoundSlotFailsClosed() {
        val summary = "₹40, Milky Mist Lactose Free Milk, 1 pack (250 ml), ₹\u200a34"
        val root = FakeNode().child(FakeNode().child(card(firstUuid, summary)))
        val runner = EqoAutomation({ root }, { EqoAutomation.ServiceState.AVAILABLE }, TakeoverDetector())
        assertTrue(runner.tap("exact:Add,exact:ADD,exact:Add to cart").isSuccess)
        // After-Add tree: the slot subtree carries an inline recommendation with its own Add control.
        val recoAdd =
            FakeNode(
                contentDescription = "Add to Cart ",
                viewIdResourceName = "${slotId(firstUuid)}-reco",
                packageName = zeptoPackage,
                isClickable = true,
            )
        val after =
            FakeNode()
                .child(
                    FakeNode()
                        .child(FakeNode(text = "Milky Mist Lactose Free Milk", className = "android.widget.TextView"))
                        .child(
                            FakeNode(
                                contentDescription = "1 item",
                                className = "android.widget.ImageView",
                                packageName = zeptoPackage,
                            ).child(FakeNode(text = "1", className = "android.widget.TextView")).child(recoAdd),
                        ).child(FakeNode(text = "1 pack (250 ml)", className = "android.widget.TextView")),
                )
        val result =
            EqoAutomation({ after }, { EqoAutomation.ServiceState.AVAILABLE }, TakeoverDetector())
                .tap(ai.eqo.core.agent.ShoppingPlans.INCREASE)
        assertTrue(result is A11yResult.Failure && result.error is A11yError.NodeNotFound)
        assertEquals(0, recoAdd.clickCount)
    }

    private val zeptoPackage = "com.zeptoconsumerapp"
    private val firstUuid = "556c3d3e-f376-4dc4-b98a-e452d14f81d8"
    private val neighbourUuid = "9a8b7c6d-1234-4567-abcd-000000000002"

    private fun containerId(uuid: String): String = "$zeptoPackage:id/product-card-container$uuid"

    private fun slotId(uuid: String): String = "$zeptoPackage:id/product-card-add-button$uuid"

    /** Observed Zepto card (before Add): summary description, price, slot with Add, name, pack. */
    private fun card(
        uuid: String,
        summary: String,
    ): FakeNode =
        FakeNode(
            contentDescription = summary,
            className = "android.view.ViewGroup",
            viewIdResourceName = containerId(uuid),
            packageName = zeptoPackage,
        ).child(
            FakeNode(text = "₹\u200a34", className = "android.widget.TextView", packageName = zeptoPackage),
        ).child(
            FakeNode(
                contentDescription = "Add to Cart ",
                className = "android.widget.Button",
                viewIdResourceName = slotId(uuid),
                packageName = zeptoPackage,
                isClickable = true,
            ),
        ).child(
            FakeNode(text = "Milky Mist Lactose Free Milk", className = "android.widget.TextView"),
        ).child(
            FakeNode(text = "1 pack (250 ml)", className = "android.widget.TextView"),
        )

    /** Observed Zepto card (after Add): the Add slot holds the quantity picker with both steppers. */
    private fun stepperCard(
        uuid: String,
        summary: String,
        quantityDesc: String,
        minus: FakeNode,
        plus: FakeNode,
    ): FakeNode =
        FakeNode(
            contentDescription = summary,
            className = "android.view.ViewGroup",
            viewIdResourceName = containerId(uuid),
            packageName = zeptoPackage,
        ).child(
            FakeNode(text = "₹\u200a34", className = "android.widget.TextView", packageName = zeptoPackage),
        ).child(
            FakeNode(
                contentDescription = quantityDesc,
                className = "android.widget.ImageView",
                packageName = zeptoPackage,
            ).child(FakeNode(text = "1", className = "android.widget.TextView")).child(minus).child(plus),
        ).child(
            FakeNode(text = "Milky Mist Lactose Free Milk", className = "android.widget.TextView"),
        ).child(
            FakeNode(text = "1 pack (250 ml)", className = "android.widget.TextView"),
        )

    private fun stepper(viewId: String): FakeNode =
        FakeNode(
            text = "",
            contentDescription = if (viewId.endsWith("-plus")) "increase quantity" else "decrease quantity",
            className = "android.widget.Button",
            viewIdResourceName = viewId,
            packageName = zeptoPackage,
            isClickable = true,
        )

    private fun neighbourAddClicks(root: FakeNode): Int =
        root
            .childAt(1)
            ?.childAt(0)
            ?.childAt(1)
            .let { it as? FakeNode }
            ?.clickCount ?: 0
}
