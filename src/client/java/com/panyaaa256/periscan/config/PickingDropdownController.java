package com.panyaaa256.periscan.config;

//? if >=1.20 {
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.utils.Dimension;
import dev.isxander.yacl3.gui.AbstractWidget;
import dev.isxander.yacl3.gui.YACLScreen;
import dev.isxander.yacl3.gui.controllers.dropdown.DropdownStringController;
import dev.isxander.yacl3.gui.controllers.dropdown.DropdownStringControllerElement;

import java.util.List;

/**
 * A dropdown that takes any text and still applies the suggestion that is
 * clicked (or chosen with the arrow keys). YACL's own dropdown only replaces
 * the typed text with the chosen suggestion when the text is not a valid
 * value, which with free input it always is.
 * <p>
 * Relies on YACL's GUI classes, which are not a stable API: checked against
 * YACL 3.6.6 to 3.9.7.
 */
final class PickingDropdownController extends DropdownStringController {
	PickingDropdownController(Option<String> option, List<String> suggestions) {
		super(option, suggestions, true, true);
	}

	@Override
	public AbstractWidget provideWidget(YACLScreen screen, Dimension<Integer> widgetDimension) {
		return new Element(this, screen, widgetDimension);
	}

	private static final class Element extends DropdownStringControllerElement {
		Element(DropdownStringController control, YACLScreen screen, Dimension<Integer> dim) {
			super(control, screen, dim);
		}

		/**
		 * Called when the dropdown closes. The typed text is itself the first
		 * suggestion, so leaving without choosing anything keeps it.
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
	}
}
//?}
