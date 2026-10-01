package com.panyaaa256.periscan.config;

//? if >=1.20 {
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.utils.Dimension;
import dev.isxander.yacl3.gui.AbstractWidget;
import dev.isxander.yacl3.gui.YACLScreen;
import dev.isxander.yacl3.gui.controllers.dropdown.DropdownStringController;
import dev.isxander.yacl3.gui.controllers.dropdown.DropdownStringControllerElement;
import dev.isxander.yacl3.gui.controllers.dropdown.DropdownWidget;

import java.util.List;
import java.util.function.Consumer;

/**
 * A dropdown that applies the suggestion that is clicked (or chosen with the
 * arrow keys or Tab), working around two problems of YACL's own:
 * <ul>
 * <li>it only replaces the typed text with the chosen suggestion when the text
 * is not a valid value, which with free input it always is;</li>
 * <li>it keeps the selection where it was when typing narrows the
 * suggestions, leaving it past the end of the list.</li>
 * </ul>
 * Relies on YACL's GUI classes, which are not a stable API: checked against
 * YACL 3.6.6 to 3.9.7.
 */
final class PickingDropdownController extends DropdownStringController {
	/**
	 * @param freeInput whether text that is not a suggestion is accepted too
	 */
	PickingDropdownController(Option<String> option, List<String> suggestions, boolean freeInput) {
		super(option, suggestions, freeInput, freeInput);
	}

	@Override
	public AbstractWidget provideWidget(YACLScreen screen, Dimension<Integer> widgetDimension) {
		return new Element(this, screen, widgetDimension);
	}

	private static final class Element extends DropdownStringControllerElement {
		private final DropdownStringController control;

		Element(DropdownStringController control, YACLScreen screen, Dimension<Integer> dim) {
			super(control, screen, dim);
			this.control = control;
		}

		/**
		 * Called when the dropdown closes. With free input the typed text is
		 * itself the first suggestion, so leaving without choosing anything
		 * keeps it.
		 */
		@Override
		public void ensureValidValue() {
			if (dropdownWidget == null || matchingValues == null || matchingValues.isEmpty()) {
				super.ensureValidValue();
				return;
			}
			inputField = matchingValues.get(Math.min(dropdownWidget.selectedIndex(), matchingValues.size() - 1));
			dropdownWidget.resetSelectedIndex();
			caretPos = getDefaultCaretPos();
			matchingValues = computeMatchingValues();
		}

		/** Same as the overridden method, but with a widget whose selection can be reset. */
		@Override
		public void createDropdownWidget() {
			dropdownVisible = true;
			dropdownWidget = new Widget(control, screen, getDimension(), this);
			screen.addPopupControllerWidget(dropdownWidget);
		}

		@Override
		public boolean modifyInput(Consumer<StringBuilder> builder) {
			boolean modified = super.modifyInput(builder);
			// The suggestions changed, so the old selection points at another entry or none.
			if (modified && dropdownWidget instanceof Widget widget) {
				widget.resetSelection();
			}
			return modified;
		}
	}

	private static final class Widget extends DropdownWidget<String> {
		Widget(DropdownStringController control, YACLScreen screen, Dimension<Integer> dim, Element element) {
			super(control, screen, dim, element);
		}

		void resetSelection() {
			selectedIndex = 0;
			firstVisibleIndex = 0;
		}
	}
}
//?}
