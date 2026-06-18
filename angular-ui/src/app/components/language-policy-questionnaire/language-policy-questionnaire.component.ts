import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatRadioModule } from '@angular/material/radio';
import { MatStepperModule } from '@angular/material/stepper';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { SimplexService } from '../../services/simplex/simplex.service';
import {
	emptyLanguageTranslationPolicy,
	initializeKeyValueTables,
	isPolicyComplete,
	LanguagePolicyQuestion,
	LanguagePolicyQuestionnaire,
	LanguageTranslationPolicy,
	parseKeyValueTable,
	policyFromApi,
	policyToRequest,
	serializeKeyValueTable
} from '../../models/language-translation-policy.model';

@Component({
	selector: 'app-language-policy-questionnaire',
	standalone: true,
	imports: [
		CommonModule,
		FormsModule,
		MatButtonModule,
		MatFormFieldModule,
		MatInputModule,
		MatRadioModule,
		MatStepperModule,
		MatProgressSpinnerModule,
		MatSnackBarModule
	],
	templateUrl: './language-policy-questionnaire.component.html',
	styleUrl: './language-policy-questionnaire.component.scss'
})
export class LanguagePolicyQuestionnaireComponent implements OnInit {
	@Input() edition = '';
	@Input() refsetId = '';
	@Input() dialectName = '';
	@Input() initialPolicy: LanguageTranslationPolicy | null = null;
	@Input() showCancel = true;
	@Input() submitLabel = 'Save policy';

	@Output() policySaved = new EventEmitter<LanguageTranslationPolicy>();
	@Output() cancelled = new EventEmitter<void>();

	questionnaire: LanguagePolicyQuestionnaire | null = null;
	policy: LanguageTranslationPolicy = emptyLanguageTranslationPolicy('');
	saving = false;
	loading = false;

	constructor(
		private simplexService: SimplexService,
		private snackBar: MatSnackBar
	) {}

	ngOnInit(): void {
		this.loadQuestionnaire();
	}

	loadQuestionnaire(): void {
		this.loading = true;
		this.simplexService.getLanguagePolicyQuestionnaire(this.edition).subscribe({
			next: (questionnaire) => {
				this.questionnaire = questionnaire;
				this.policy = emptyLanguageTranslationPolicy(questionnaire.version);
				initializeKeyValueTables(questionnaire, this.policy.policyItems);

				if (this.initialPolicy) {
					this.applyPolicy(this.initialPolicy);
					this.loading = false;
				} else if (this.refsetId) {
					this.loadExistingPolicy();
				} else {
					this.loading = false;
				}
			},
			error: () => {
				this.loading = false;
				this.snackBar.open('Failed to load language policy questionnaire.', 'Close', { duration: 5000 });
			}
		});
	}

	loadExistingPolicy(): void {
		this.simplexService.getLanguagePolicy(this.edition, this.refsetId).subscribe({
			next: (data) => {
				this.applyPolicy(data);
				this.loading = false;
			},
			error: (err) => {
				this.loading = false;
				if (err?.status !== 404) {
					this.snackBar.open('Failed to load language policy.', 'Close', { duration: 4000 });
				}
			}
		});
	}

	private applyPolicy(data: LanguageTranslationPolicy): void {
		const loaded = policyFromApi(data);
		this.policy.questionnaireVersion = loaded.questionnaireVersion || this.questionnaire!.version;
		this.policy.policyItems = { ...this.policy.policyItems, ...loaded.policyItems };
		if (this.questionnaire) {
			initializeKeyValueTables(this.questionnaire, this.policy.policyItems);
		}
	}

	onCancel(): void {
		this.cancelled.emit();
	}

	onSave(): void {
		if (!this.questionnaire || !this.isComplete()) {
			this.snackBar.open('Please answer all required questions before saving.', 'Close', { duration: 4000 });
			return;
		}
		this.saving = true;
		this.simplexService.saveLanguagePolicy(this.edition, this.refsetId, policyToRequest(this.policy)).subscribe({
			next: (saved) => {
				this.saving = false;
				this.policySaved.emit(saved);
			},
			error: () => {
				this.saving = false;
				this.snackBar.open('Failed to save language policy.', 'Close', { duration: 5000 });
			}
		});
	}

	isComplete(): boolean {
		return this.questionnaire ? isPolicyComplete(this.questionnaire, this.policy.policyItems) : false;
	}

	getKeyValueTable(question: LanguagePolicyQuestion): Record<string, string> {
		return parseKeyValueTable(this.policy.policyItems[question.id]);
	}

	setKeyValueCell(question: LanguagePolicyQuestion, key: string, value: string): void {
		const table = this.getKeyValueTable(question);
		table[key] = value;
		this.policy.policyItems[question.id] = serializeKeyValueTable(table);
	}

	selectedOption(question: LanguagePolicyQuestion) {
		const value = this.policy.policyItems[question.id];
		return question.options?.find(o => o.value === value);
	}

	shouldShowFreeText(question: LanguagePolicyQuestion): boolean {
		const option = this.selectedOption(question);
		return !!option?.allowFreeText && !!option.freeTextKey;
	}

	freeTextKey(question: LanguagePolicyQuestion): string {
		return this.selectedOption(question)?.freeTextKey || '';
	}
}
