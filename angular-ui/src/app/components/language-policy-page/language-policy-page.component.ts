import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { LanguagePolicyQuestionnaireComponent } from '../language-policy-questionnaire/language-policy-questionnaire.component';
import { LanguageTranslationPolicy } from '../../models/language-translation-policy.model';

@Component({
	selector: 'app-language-policy-page',
	standalone: true,
	imports: [
		CommonModule,
		MatButtonModule,
		MatIconModule,
		MatSnackBarModule,
		LanguagePolicyQuestionnaireComponent
	],
	templateUrl: './language-policy-page.component.html',
	styleUrl: './language-policy-page.component.scss'
})
export class LanguagePolicyPageComponent implements OnInit {
	edition = '';
	refsetId = '';
	dialectName = '';
	languageCode = '';

	constructor(
		private route: ActivatedRoute,
		private router: Router,
		private snackBar: MatSnackBar
	) {}

	ngOnInit(): void {
		this.route.paramMap.subscribe(() => {
			this.edition = this.route.snapshot.paramMap.get('edition') ?? '';
			this.refsetId = this.route.snapshot.paramMap.get('refsetId') ?? '';
			this.updateQueryDerivedFields();
		});
		this.route.queryParamMap.subscribe(() => {
			this.updateQueryDerivedFields();
		});
	}

	private updateQueryDerivedFields(): void {
		const dialect = this.route.snapshot.queryParamMap.get('dialect')?.trim();
		this.dialectName = dialect || this.refsetId;
		this.languageCode = this.route.snapshot.queryParamMap.get('lang')?.trim() || '';
	}

	goBack(): void {
		void this.router.navigate(['/translation-studio', this.edition]);
	}

	onPolicySaved(_policy: LanguageTranslationPolicy): void {
		this.snackBar.open('Language policy saved.', 'Close', { duration: 4000 });
		this.goBack();
	}

	onCancel(): void {
		this.goBack();
	}
}
