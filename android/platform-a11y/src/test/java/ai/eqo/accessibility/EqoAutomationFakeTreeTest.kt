package ai.eqo.accessibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TASK-009 acceptance criteria on fake node trees: observe, tap, scroll, text
 * input through [EqoAutomation]; user touch during an agent action pauses the
 * loop; accessibility disabled mid-task gives a typed error with no silent
 * retry.
 */
class EqoAutomationFakeTreeTest {
    private val takeover = TakeoverDetector()
    private var serviceState = EqoAutomation.ServiceState.AVAILABLE
    private var rootProviderCalls = 0

    @Test fun zeptoObservedProductCardIdsAndTrimmedAddDescriptionAreNotPaymentCards() {
        val packageName = "com.zeptoconsumerapp"
        val suffix = "00000000-0000-0000-0000-000000000001"
        val add =
            FakeNode(
                contentDescription = "Add to Cart ",
                viewIdResourceName = "$packageName:id/product-card-add-button$suffix",
                packageName = packageName,
                className = "android.widget.Button",
                isClickable = true,
            ).child(FakeNode(text = "ADD", packageName = packageName))
        val row =
            FakeNode(
                viewIdResourceName = "$packageName:id/product-card-container$suffix",
                packageName = packageName,
                className = "android.view.ViewGroup",
                isClickable = true,
            ).child(FakeNode(text = "Synthetic milk", packageName = packageName))
                .child(add)
        val later = FakeNode(text = "Add", isClickable = true)
        val runner = automation(FakeNode(packageName = packageName).child(row).child(later))
        assertTrue(runner.tap("exact:Add,exact:ADD,exact:Add to cart").isSuccess)
        assertEquals(listOf(1, 0, 0), listOf(add, row, later).map { it.clickCount })
    }

    @Test fun zeptoProductIdExceptionNeverExemptsPaymentLabelsAncestorsOrOtherCardIds() {
        val packageName = "com.zeptoconsumerapp"
        val productId = "$packageName:id/product-card-add-button00000000-0000-0000-0000-000000000001"
        for (label in listOf("Add card", "Pay", "COD", "Confirm address", "Place order")) {
            val add =
                FakeNode(text = "Add", viewIdResourceName = productId, packageName = packageName, isClickable = true)
            val parent = FakeNode(contentDescription = label).child(add)
            assertFalse(automation(FakeNode().child(parent)).tap("exact:Add").isSuccess)
            assertEquals(0, add.clickCount)
            val payment =
                FakeNode(
                    text = "Add",
                    contentDescription = label,
                    viewIdResourceName = productId,
                    isClickable = true,
                    packageName = packageName,
                )
            assertFalse(automation(FakeNode().child(payment)).tap("exact:Add").isSuccess)
            assertEquals(0, payment.clickCount)
        }
        for (id in listOf(productId + "_pay", productId + "_card", "$packageName:id/payment_card", productId)) {
            val add =
                FakeNode(
                    text = "Add",
                    viewIdResourceName = id,
                    isClickable = true,
                    packageName = if (id == productId) "other.shop" else packageName,
                )
            assertFalse(automation(FakeNode().child(add)).tap("exact:Add").isSuccess)
            assertEquals(0, add.clickCount)
        }
    }

    @Test fun zeptoObservedSearchIdsAvoidRotatingPromptsAndNeverFallBackToOtherFields() {
        val packageName = "com.zeptoconsumerapp"
        val search = FakeNode(viewIdResourceName = "$packageName:id/homepage-search-box", isClickable = true)
        val input =
            FakeNode(viewIdResourceName = "$packageName:id/search-query-input", className = "android.widget.EditText")
        val payment = FakeNode(text = "Card number", isEditable = true)
        val runner = automation(FakeNode(packageName = packageName).child(search).child(input).child(payment))
        assertTrue(runner.tap("id:$packageName:id/homepage-search-box").isSuccess)
        assertTrue(runner.type("search:id:$packageName:id/search-query-input", "milk").isSuccess)
        assertEquals("milk", input.typedValue)
        assertNull(payment.typedValue)
        val missing = automation(FakeNode(packageName = packageName).child(payment))
        assertFalse(missing.tap("id:$packageName:id/homepage-search-box").isSuccess)
        assertFalse(missing.type("search:id:$packageName:id/search-query-input", "milk").isSuccess)
        assertNull(payment.typedValue)
    }

    @Test fun shoppingAlternativeLabelsKeepProductOrderAndRefusePaymentAncestors() {
        val first = FakeNode(text = "Add to cart", isClickable = true)
        val later = FakeNode(text = "Add", isClickable = true)
        assertTrue(automation(FakeNode().child(first).child(later)).tap("exact:Add,exact:Add to cart").isSuccess)
        assertEquals(listOf(1, 0), listOf(first, later).map { it.clickCount })
        for (target in listOf("Pay", "COD", "Confirm address", "Place order")) {
            val parent = FakeNode(contentDescription = target, isClickable = true).child(FakeNode(text = "Add"))
            assertFalse(automation(FakeNode().child(parent)).tap("exact:Add").isSuccess)
            assertEquals(0, parent.clickCount)
        }
    }

    @Test fun shoppingExactControlsNeverFallBackToPaymentLabelsAndSearchNeverTypesInCardField() {
        val payment = FakeNode(text = "Add card", isClickable = true)
        val checkout = FakeNode(contentDescription = "Cart checkout confirm", isClickable = true)
        val field = FakeNode(text = "Card number", isEditable = true)
        val pincode = FakeNode(hintText = "Search or type your pincode", isEditable = true)
        val runner =
            automation(
                FakeNode()
                    .child(payment)
                    .child(checkout)
                    .child(field)
                    .child(pincode),
            )
        assertFalse(runner.tap("exact:Add,exact:Add to cart").isSuccess)
        assertFalse(runner.tap("exact:Cart,exact:View cart").isSuccess)
        assertFalse(runner.type("search:Search", "milk").isSuccess)
        assertEquals(0, payment.clickCount)
        assertEquals(0, checkout.clickCount)
        assertNull(field.typedValue)
        assertNull(pincode.typedValue)
    }

    @Test fun shoppingControlsChooseFirstAddAndSupportContentDescriptions() {
        val first = FakeNode(text = "Add", isClickable = true)
        val second = FakeNode(text = "Add", isClickable = true)
        val increase = FakeNode(contentDescription = "Increase quantity", isClickable = true)
        val cart = FakeNode(contentDescription = "View cart", isClickable = true)
        val search = FakeNode(hintText = "Search for products", isEditable = true)
        val runner =
            automation(
                FakeNode()
                    .child(FakeNode().child(FakeNode(text = "Milk")).child(first).child(increase))
                    .child(FakeNode().child(FakeNode(text = "Bread")).child(second))
                    .child(cart)
                    .child(search),
            )
        assertTrue(runner.type("search:Search", "milk").isSuccess)
        assertTrue(runner.tap("exact:Add,exact:Add to cart").isSuccess)
        assertTrue(runner.tap("exact:+,exact:Increase quantity").isSuccess)
        assertTrue(runner.tap("exact:Cart,exact:View cart").isSuccess)
        assertEquals(listOf(1, 0, 1, 1), listOf(first, second, increase, cart).map { it.clickCount })
        assertEquals("milk", search.typedValue)
    }

    private fun automation(root: FakeNode?): EqoAutomation =
        EqoAutomation(
            rootProvider = {
                rootProviderCalls++
                root
            },
            serviceState = { serviceState },
            takeover = takeover,
        )

    @Test fun shoppingCodIdsOnControlsAndNonClickableAncestorsAreRefused() {
        for (id in listOf("shop:id/cod_button", "shop:id/select_cod")) {
            for (label in listOf("Add", "Cart")) {
                val button = FakeNode(text = label, isClickable = true)
                val parent = FakeNode(viewIdResourceName = id).child(button)
                assertFalse(automation(FakeNode().child(parent)).tap("exact:$label").isSuccess)
                assertEquals(0, button.clickCount)
                val direct = FakeNode(text = label, viewIdResourceName = id, isClickable = true)
                assertFalse(automation(FakeNode().child(direct)).tap("exact:$label").isSuccess)
                assertEquals(0, direct.clickCount)
            }
        }
    }

    @Test fun shoppingIncreasesStayWithAddedProductAcrossFreshTreesAndMultipleRows() {
        val breadIncrease = FakeNode(text = "+", isClickable = true)
        val add = FakeNode(text = "Add", isClickable = true)
        val laterAdd = FakeNode(text = "Add", isClickable = true)
        val bread = FakeNode().child(FakeNode(text = "Bread")).child(breadIncrease)
        var root =
            FakeNode(packageName = "shop")
                .child(bread)
                .child(FakeNode().child(FakeNode(text = "Milk")).child(add))
                .child(FakeNode().child(FakeNode(text = "Apples")).child(laterAdd))
        val runner = EqoAutomation({ root }, { serviceState }, takeover)
        assertTrue(runner.tap("exact:Add,exact:ADD,exact:Add to cart").isSuccess)
        val milkIncrease = FakeNode(contentDescription = "Increase quantity", isClickable = true)
        root =
            FakeNode(packageName = "shop")
                .child(bread)
                .child(FakeNode().child(FakeNode(text = "Milk")).child(milkIncrease))
                .child(FakeNode().child(FakeNode(text = "Apples")).child(laterAdd))
        repeat(2) { assertTrue(runner.tap("exact:+,exact:Increase quantity").isSuccess) }
        assertEquals(listOf(1, 0, 0, 2), listOf(add, laterAdd, breadIncrease, milkIncrease).map { it.clickCount })
    }

    @Test fun shoppingMissingAmbiguousReorderedOrDuplicateProductIncreaseStopsBeforeCart() {
        for (problem in listOf("missing", "ambiguous", "reordered", "duplicate", "unbound")) {
            val add = FakeNode(text = "Add", isClickable = true)
            val breadIncrease = FakeNode(text = "+", isClickable = true)
            val milkIncrease = FakeNode(text = "+", isClickable = true)
            val cart = FakeNode(text = "Cart", isClickable = true)
            val bread = FakeNode().child(FakeNode(text = "Bread")).child(breadIncrease)
            val milk = FakeNode().child(FakeNode(text = "Milk")).child(add)
            var root = FakeNode().child(bread).child(milk).child(cart)
            val runner = EqoAutomation({ root }, { serviceState }, takeover)
            if (problem != "unbound") assertTrue(runner.tap("exact:Add,exact:Add to cart").isSuccess)
            val changedMilk = FakeNode().child(FakeNode(text = "Milk"))
            if (problem != "missing") changedMilk.child(milkIncrease)
            if (problem == "ambiguous") changedMilk.child(FakeNode(text = "+", isClickable = true))
            root =
                if (problem == "reordered") {
                    FakeNode().child(changedMilk).child(bread).child(cart)
                } else {
                    FakeNode().child(bread).child(changedMilk).child(cart)
                }
            if (problem == "duplicate") root.child(FakeNode().child(FakeNode(text = "Milk")))
            val actions = listOf("exact:+,exact:Increase quantity", "exact:Cart")
            for (target in actions) {
                if (!runner.tap(target).isSuccess) break
            }
            assertEquals(problem, listOf(0, 0, 0), listOf(breadIncrease, milkIncrease, cart).map { it.clickCount })
        }
    }

    @Test
    fun keepSpeedDialAndEditorUseIdsWithoutLabelsOrSearchFallback() {
        val fab =
            FakeNode(
                contentDescription = "Create a note",
                viewIdResourceName = "com.google.android.keep:id/speed_dial_create_close_button",
                isClickable = true,
            )
        val items =
            listOf("new_list_button", "new_drawing_button", "new_photo_note", "new_note_button")
                .map { FakeNode(viewIdResourceName = "com.google.android.keep:id/$it", isClickable = true) }
        val title = FakeNode(viewIdResourceName = "com.google.android.keep:id/editable_title", isEditable = true)
        val body = FakeNode(viewIdResourceName = "com.google.android.keep:id/edit_note_text", isEditable = true)
        val toolbar =
            FakeNode(
                contentDescription = "Search Keep",
                viewIdResourceName = "com.google.android.keep:id/toolbar",
                isEditable = true,
            )
        var root = FakeNode().child(toolbar).child(fab)
        val runner =
            EqoAutomation(
                rootProvider = { root },
                serviceState = { serviceState },
                takeover = takeover,
                ownPackage = "ai.eqo.app",
            )
        assertTrue(runner.tap("Create a note").isSuccess)
        root = FakeNode().child(toolbar)
        items.forEach(root::child)
        assertTrue(runner.tap("id:new_note_button").isSuccess)
        assertEquals(listOf(0, 0, 0, 1), items.map { it.clickCount })
        root = FakeNode().child(toolbar).child(title).child(body)
        assertTrue(runner.type("id:editable_title", "Test").isSuccess)
        assertTrue(runner.type("id:edit_note_text", "Example body").isSuccess)
        assertEquals("Test", title.typedValue)
        assertEquals("Example body", body.typedValue)
        assertNull(toolbar.typedValue)
        root = FakeNode().child(toolbar)
        assertFalse(runner.type("id:editable_title", "Never search").isSuccess)
        assertNull(toolbar.typedValue)
        assertFalse(runner.tap("Create a note").isSuccess)
        root = FakeNode(packageName = "ai.eqo.app").child(title).child(items.last())
        assertFalse(runner.type("id:editable_title", "Never approve").isSuccess)
        assertFalse(runner.tap("id:new_note_button").isSuccess)
        assertEquals("Test", title.typedValue)
        assertEquals(1, items.last().clickCount)
    }

    @Test
    fun explicitIdNeverMatchesLabelDecoysPartialIdsOrPasswords() {
        val label = FakeNode(text = "id:new_note_button", isClickable = true)
        val partial = FakeNode(viewIdResourceName = "keep:id/other_new_note_button", isClickable = true)
        val password = FakeNode(viewIdResourceName = "keep:id/new_note_button", isPassword = true, isClickable = true)
        val runner = automation(FakeNode().child(label).child(partial).child(password))
        assertFalse(runner.tap("id:new_note_button").isSuccess)
        assertFalse(runner.tap("id:").isSuccess)
        assertEquals(listOf(0, 0, 0), listOf(label, partial, password).map { it.clickCount })
    }

    @Test
    fun whatsappSearchTemplateStopsOnRestoredChatWithoutChangingComposer() {
        val composer =
            FakeNode(text = "Message", viewIdResourceName = "com.whatsapp:id/entry", isEditable = true)
        val runner = automation(FakeNode(packageName = "com.whatsapp").child(composer))
        val steps = whatsappSearchSteps()
        val dispatched = mutableListOf<String>()
        for (step in steps) {
            dispatched += step.action.name
            val result = dispatchSearch(runner, step.action)
            if (!result.isSuccess) break
        }
        assertEquals(listOf("OPEN_APP", "WAIT", "CLICK_TEXT"), dispatched)
        assertNull(composer.typedValue)
        // Even if navigation falsely succeeded, strict typing itself must refuse the sole composer.
        assertFalse(dispatchSearch(runner, steps.last().action).isSuccess)
        assertNull(composer.typedValue)
        assertTrue(composer.actions.isEmpty())
    }

    @Test
    fun whatsappSearchTemplateTypesOnlyInGenuineSearchWithoutEnter() {
        val searchButton = FakeNode(viewIdResourceName = "com.whatsapp:id/menuitem_search", isClickable = true)
        val searchField = FakeNode(viewIdResourceName = "com.whatsapp:id/search_src_text", isEditable = true)
        val composer = FakeNode(text = "Message", viewIdResourceName = "com.whatsapp:id/entry", isEditable = true)
        var root = FakeNode(packageName = "com.whatsapp").child(searchButton).child(composer)
        val runner =
            EqoAutomation({ root }, { serviceState }, takeover)
        val steps = whatsappSearchSteps()
        for (step in steps) {
            assertTrue(dispatchSearch(runner, step.action).isSuccess)
            if (step.action.name == "CLICK_TEXT") {
                root = FakeNode(packageName = "com.whatsapp").child(searchField).child(composer)
            }
        }
        assertEquals(1, searchButton.clickCount)
        assertEquals("Synthetic query", searchField.typedValue)
        assertNull(composer.typedValue)
        assertTrue(composer.actions.isEmpty())
        assertFalse(steps.any { it.action.name == "PRESS_ENTER" })
    }

    private fun whatsappSearchSteps(): List<ai.eqo.core.agent.LoopStep> =
        requireNotNull(
            ai.eqo.core.agent.IntentSkills.plan(
                ai.eqo.core.agent.TaskIntent(
                    "Search WhatsApp",
                    "WhatsApp",
                    "search",
                    mapOf("query" to "Synthetic query"),
                    send = "no",
                ),
                setOf("OPEN_APP", "WAIT", "CLICK_TEXT", "TYPE_TEXT", "PRESS_ENTER"),
            ),
        )

    private fun dispatchSearch(
        runner: EqoAutomation,
        action: ai.eqo.core.agent.ExecutedAction,
    ): A11yResult =
        when (action.name) {
            "OPEN_APP", "WAIT" -> A11yResult.success("Fake launch/wait")
            "CLICK_TEXT" -> runner.tap(action.params.getValue("text"))
            "TYPE_TEXT" -> runner.type(action.params.getValue("searchText"), action.params.getValue("content"))
            else -> error("Unexpected dispatch, including Enter: ${action.name}")
        }

    /** A small "test app" screen: headline, decoy, target row, text field, list. */
    private fun probeScreen(): Pair<FakeNode, Screen> {
        val root = FakeNode()
        val headline = FakeNode(text = "Probe screen")
        val decoy = FakeNode(text = "Send later", isClickable = true)
        val target = FakeNode(text = "Send")
        val row = FakeNode(isClickable = true)
        row.child(target)
        val field = FakeNode(text = "Message", isEditable = true)
        val list = FakeNode(isScrollable = true)
        root
            .child(headline)
            .child(decoy)
            .child(row)
            .child(field)
            .child(list)
        return root to Screen(root, decoy, target, row, field, list)
    }

    private data class Screen(
        val root: FakeNode,
        val decoy: FakeNode,
        val target: FakeNode,
        val row: FakeNode,
        val field: FakeNode,
        val list: FakeNode,
    )

    @Test
    fun keepEntryAlternativesMatchTextBarAndContentDescriptionFab() {
        listOf(
            FakeNode(text = "Take a note", isClickable = true),
            FakeNode(contentDescription = "New text note", isClickable = true),
        ).forEach { entry ->
            assertTrue(automation(FakeNode().child(entry)).tap("Take a note, New text note").isSuccess)
            assertEquals(1, entry.clickCount)
        }
    }

    @Test
    fun keepTitleAndNoteFieldsAcceptOnlyTheirRequestedContent() {
        val title = FakeNode(hintText = "Title", isEditable = true)
        val body = FakeNode(hintText = "Note", isEditable = true)
        val app = automation(FakeNode().child(title).child(body))
        assertTrue(app.type("Title", "Test").isSuccess)
        assertTrue(app.type("Note", "Example body").isSuccess)
        assertEquals("Test", title.typedValue)
        assertEquals("Example body", body.typedValue)
    }

    @Test
    fun exactAlternativeBeatsEarlierPartialAndUsesDeclaredOrder() {
        val partial = FakeNode(text = "Take a note later", isClickable = true)
        val fab = FakeNode(contentDescription = "New text note", isClickable = true)
        val bar = FakeNode(text = "Take a note", isClickable = true)
        val root = FakeNode().child(partial).child(fab)
        assertTrue(automation(root).tap("Take a note,New text note").isSuccess)
        assertEquals(0, partial.clickCount)
        assertEquals(1, fab.clickCount)
        root.child(bar)
        assertTrue(automation(root).tap("Take a note,New text note").isSuccess)
        assertEquals(1, bar.clickCount)
        assertEquals(1, fab.clickCount)
    }

    @Test
    fun alternativesMatchIdSuffixAndIgnoreEmptyLabelsAndPasswords() {
        val secret = FakeNode(text = "Take a note", isPassword = true, isClickable = true)
        val entry = FakeNode(viewIdResourceName = "com.google.android.keep:id/new_note", isClickable = true)
        val root = FakeNode().child(secret).child(entry)
        assertTrue(automation(root).tap(" ,Take a note,new_note, ").isSuccess)
        assertEquals(0, secret.clickCount)
        assertEquals(1, entry.clickCount)
        assertFalse(automation(root).tap(" , , ").isSuccess)
    }

    @Test
    fun alternativesFallBackToPartialButNeverTryAnotherControlAfterRejection() {
        val partial = FakeNode(contentDescription = "Create new text note", isClickable = true)
        assertTrue(automation(FakeNode().child(partial)).tap("Take a note,New text note").isSuccess)
        val first = FakeNode(text = "Take a note", isClickable = true).apply { rejectActions = true }
        val second = FakeNode(contentDescription = "New text note", isClickable = true)
        val result = automation(FakeNode().child(first).child(second)).tap("Take a note,New text note")
        assertTrue((result as A11yResult.Failure).error is A11yError.ActionRejected)
        assertEquals(listOf("click"), first.actions)
        assertEquals(0, second.clickCount)
    }

    // ── observe ───────────────────────────────────────────────────────────

    @Test
    fun observeReturnsEveryNodeTextDepthFirst() {
        val (root, _) = probeScreen()
        val result = automation(root).observe()
        val detail = (result as A11yResult.Success).detail
        assertTrue(detail.contains("Probe screen"))
        assertTrue(detail.contains("Send later"))
        assertTrue(detail.contains("Send"))
        assertTrue(detail.contains("Message"))
    }

    @Test
    fun observeWithoutAWindowIsATypedNodeNotFound() {
        val result = automation(null).observe()
        assertTrue(result is A11yResult.Failure)
        assertEquals(A11yError.NodeNotFound("active window"), (result as A11yResult.Failure).error)
    }

    // ── tap ───────────────────────────────────────────────────────────────

    @Test
    fun tapPrefersAnExactLabelOverAnEarlierPartialMatch() {
        // Changed on purpose (2026-10-07): the inherited "first substring match wins" rule tapped "Video call"
        // when the plan said "Call". An exact label now wins; "Send later" is only a fallback for "Send".
        val (root, screen) = probeScreen()
        val result = automation(root).tap("Send")
        assertTrue(result.isSuccess)
        assertEquals(0, screen.decoy.clickCount)
        assertEquals(1, screen.row.clickCount)
    }

    @Test
    fun tapOnNonClickableLeafBubblesToTheClickableAncestorRow() {
        val target = FakeNode(text = "Send")
        val row = FakeNode(isClickable = true).child(target)
        val root = FakeNode().child(row)
        val result = automation(root).tap("Send")
        assertTrue(result.isSuccess)
        assertEquals(1, row.clickCount)
        assertEquals("the leaf itself is not clickable", 0, target.clickCount)
    }

    @Test
    fun tapWithNoMatchIsTypedNodeNotFoundAndClicksNothing() {
        val (root, screen) = probeScreen()
        val result = automation(root).tap("No such label")
        assertEquals(A11yError.NodeNotFound("No such label"), (result as A11yResult.Failure).error)
        assertEquals(0, screen.row.clickCount)
        assertEquals(0, screen.decoy.clickCount)
        assertEquals(0, screen.target.clickCount)
    }

    @Test
    fun tapByIdTargetsTheMatchingResourceId() {
        val target = FakeNode(viewIdResourceName = "ai.eqo.app:id/target", isClickable = true)
        val decoy = FakeNode(viewIdResourceName = "ai.eqo.app:id/decoy", isClickable = true)
        val root = FakeNode().child(target).child(decoy)
        val result = automation(root).tapById("ai.eqo.app:id/target")
        assertTrue(result.isSuccess)
        assertEquals(1, target.clickCount)
        assertEquals(0, decoy.clickCount)
    }

    @Test
    fun tapRejectedByThePlatformIsATypedActionRejected() {
        val target = FakeNode(text = "Send", isClickable = true)
        target.rejectActions = true
        val root = FakeNode().child(target)
        val result = automation(root).tap("Send")
        assertEquals(A11yError.ActionRejected("Send"), (result as A11yResult.Failure).error)
    }

    // ── text input ────────────────────────────────────────────────────────

    @Test
    fun typeFillsTheFirstEditableMatch() {
        val (root, screen) = probeScreen()
        val result = automation(root).type("Message", "hello EQO")
        assertTrue(result.isSuccess)
        assertEquals("hello EQO", screen.field.typedValue)
    }

    @Test
    fun typeWithoutAnEditableMatchIsTypedNodeNotFound() {
        val (root, screen) = probeScreen()
        // A second input makes the unmatched hint ambiguous, so EQO must not guess.
        root.child(FakeNode(text = "Subject", isEditable = true))
        val result = automation(root).type("Absent field", "hello")
        assertEquals(A11yError.NodeNotFound("Absent field"), (result as A11yResult.Failure).error)
        assertNull(screen.field.typedValue)
    }

    @Test
    fun typeByIdFillsTheMatchingField() {
        val field = FakeNode(viewIdResourceName = "ai.eqo.app:id/compose", isEditable = true)
        val root = FakeNode().child(field)
        val result = automation(root).typeById("ai.eqo.app:id/compose", "typed by id")
        assertTrue(result.isSuccess)
        assertEquals("typed by id", field.typedValue)
    }

    // ── scroll ────────────────────────────────────────────────────────────

    @Test
    fun scrollScrollsTheFirstScrollableNode() {
        val (root, screen) = probeScreen()
        val result = automation(root).scroll(forward = true)
        assertTrue(result.isSuccess)
        assertEquals(1, screen.list.scrollCount)
    }

    @Test
    fun scrollWithoutAScrollableNodeIsTypedNodeNotFound() {
        val root = FakeNode(text = "Static screen")
        val result = automation(root).scroll(forward = true)
        assertEquals(A11yError.NodeNotFound("scrollable node"), (result as A11yResult.Failure).error)
    }

    @Test
    fun scrollContinuesIntoARejectedScrollableAncestor() {
        val child = FakeNode(isScrollable = true)
        val root = FakeNode(isScrollable = true).apply { rejectActions = true }.child(child)
        assertTrue(automation(root).scroll(forward = true).isSuccess)
        assertEquals(0, root.scrollCount)
        assertEquals(1, child.scrollCount)
    }

    @Test
    fun scrollContinuesPastARejectedContainerToALaterSibling() {
        val first = FakeNode(isScrollable = true).apply { rejectActions = true }
        val second = FakeNode(isScrollable = true)
        val third = FakeNode(isScrollable = true)
        val root = FakeNode().child(first).child(second).child(third)
        assertTrue(automation(root).scroll(forward = false).isSuccess)
        assertEquals(0, first.scrollCount)
        assertEquals(1, second.scrollCount)
        assertEquals("must stop after the first accepted action", 0, third.scrollCount)
    }

    @Test
    fun scrollDistinguishesMissingRootFromRejectedContainers() {
        val missing = automation(null).scroll(forward = true) as A11yResult.Failure
        assertEquals(A11yError.NodeNotFound("active window"), missing.error)
        val rejected = FakeNode(isScrollable = true).apply { rejectActions = true }
        val result = automation(rejected).scroll(forward = true) as A11yResult.Failure
        assertEquals(A11yError.ActionRejected("scroll"), result.error)
    }

    // ── takeover: user touch during an agent action pauses the loop ───────

    @Test
    fun userTouchDuringAnAgentActionPausesTheLoopAndLaterActionsRefuseToRun() {
        val (root, screen) = probeScreen()
        val automation = automation(root)

        // The user touches the screen while a tap action is in flight. The
        // probe node stands in for the service's touch probe (ACTION_OUTSIDE /
        // TYPE_TOUCH_INTERACTION_START feed the same detector in production).
        val actingTarget = FakeNode(text = "Act", isClickable = true)
        val probe = TouchObservingNode(takeover, actingTarget)
        val actingRoot = FakeNode().child(probe)
        val duringAction =
            EqoAutomation(
                rootProvider = { actingRoot },
                serviceState = { serviceState },
                takeover = takeover,
            )
        val result = duringAction.tap("Act")
        assertTrue(result.isSuccess)
        assertTrue("takeover must fire on the in-action user touch", probe.userTouchTriggeredTakeover)
        assertTrue(takeover.isPaused)

        // The loop is paused: no further action runs, nothing else is clicked.
        val afterPause = automation.tap("Send")
        assertEquals(A11yError.TakeoverDetected, (afterPause as A11yResult.Failure).error)
        assertEquals(0, screen.decoy.clickCount)
        assertEquals(0, screen.row.clickCount)
    }

    @Test
    fun userTouchWhileTheAgentIsIdleIsFreeInteraction() {
        val (root, screen) = probeScreen()
        // Touch while nothing is in flight: not a takeover.
        val triggered = takeover.onTouch(TakeoverDetector.TouchSource.USER)
        assertFalse(triggered)
        assertFalse(takeover.isPaused)
        assertTrue(automation(root).tap("Send").isSuccess)
        assertEquals(1, screen.row.clickCount)
    }

    @Test
    fun afterResumeTheLoopRunsAgain() {
        val (root, screen) = probeScreen()
        takeover.onAgentActionStarted()
        takeover.onTouch(TakeoverDetector.TouchSource.USER)
        takeover.onAgentActionFinished()
        assertTrue(takeover.isPaused)
        takeover.resume(
            ai.eqo.core.agent.UserResumeConfirmation
                .forExplicitUserConfirmation(1L),
        )
        assertFalse(takeover.isPaused)
        assertTrue(automation(root).tap("Send").isSuccess)
        assertEquals(1, screen.row.clickCount)
    }

    // ── accessibility disabled mid-task: typed error, no silent retry ─────

    @Test
    fun disabledServiceGivesATypedErrorOnTheFirstAttemptAndNeverRetries() {
        val (root, screen) = probeScreen()
        serviceState = EqoAutomation.ServiceState.ACCESSIBILITY_DISABLED
        val automation = automation(root)

        val result = automation.tap("Send")

        assertEquals(A11yError.AccessibilityDisabled, (result as A11yResult.Failure).error)
        assertEquals("the node tree must not be read when the service is gone", 0, rootProviderCalls)
        assertEquals("no silent retry, no side effects", 0, screen.target.clickCount)
    }

    @Test
    fun everyTypedActionReportsTheDisabledServiceAsTheSameTypedError() {
        val (root, _) = probeScreen()
        serviceState = EqoAutomation.ServiceState.ACCESSIBILITY_DISABLED
        val automation = automation(root)
        val results =
            listOf(
                automation.observe(),
                automation.tap("Send"),
                automation.tapById("ai.eqo.app:id/target"),
                automation.type("Message", "x"),
                automation.typeById("ai.eqo.app:id/compose", "x"),
                automation.scroll(forward = true),
            )
        results.forEach { result ->
            assertEquals(A11yError.AccessibilityDisabled, (result as A11yResult.Failure).error)
        }
    }

    /**
     * A node whose "platform" reports a user touch mid-action (standing in for
     * the service's touch probe) and whose tap runs through a whole agent action.
     */
    private class TouchObservingNode(
        private val takeover: TakeoverDetector,
        private val inner: FakeNode,
    ) : A11yNode by inner {
        var userTouchTriggeredTakeover = false

        override fun click(): Boolean {
            // Mid-action, the user touches the screen.
            userTouchTriggeredTakeover = takeover.onTouch(TakeoverDetector.TouchSource.USER)
            return inner.click()
        }
    }
}
