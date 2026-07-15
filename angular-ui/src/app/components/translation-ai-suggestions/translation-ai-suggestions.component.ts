import {Component, EventEmitter, Input, Output} from '@angular/core';

@Component({
	selector: 'app-translation-ai-suggestions',
	templateUrl: './translation-ai-suggestions.component.html',
	styleUrl: './translation-ai-suggestions.component.scss'
})
export class TranslationAiSuggestionsComponent {
	@Input() suggestions: string[] | null | undefined = [];
	@Input() accepting = false;
	@Input() compact = false;

	@Output() acceptSuggestion = new EventEmitter<string>();

	onAccept(suggestion: string): void {
		if (!this.accepting) {
			this.acceptSuggestion.emit(suggestion);
		}
	}

	visibleSuggestions(): string[] {
		return Array.isArray(this.suggestions) ? this.suggestions.filter((s) => (s ?? '').trim().length > 0) : [];
	}
}
