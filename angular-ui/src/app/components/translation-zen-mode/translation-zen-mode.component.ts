import {ChangeDetectorRef, Component, OnDestroy, OnInit} from '@angular/core';
import {FormArray, FormBuilder, FormControl, FormGroup, Validators} from '@angular/forms';
import {ActivatedRoute, Router} from '@angular/router';
import {MatSnackBar} from '@angular/material/snack-bar';
import {Subscription, firstValueFrom} from 'rxjs';
import {SimplexService} from 'src/app/services/simplex/simplex.service';
import {
	buildUpdateBody,
	normalizeTargetTerms,
	syncStatusWithTranslationText as syncStatusWithTranslationTextHelper,
	termsChangedFromLoaded,
	TranslationUpdateBody
} from 'src/app/utils/translation-unit-form.helper';
import {TRANSLATION_STATUS_RADIO_ORDER, translationStatusRadioLabel} from 'src/app/utils/translation-status-label';
import {mergeTranslationStudioQueryParams, parseTranslationEnglishSearch, parseTranslationTargetSearch, parseTranslationStatusFilter} from 'src/app/utils/translation-studio-query-params';

export interface ZenUnitState {
	context: string;
	sourceTerm: string;
	form: FormGroup;
	loadedTargetTerms: string[];
	loadedStatus: string;
	saving: boolean;
	saveError: string | null;
	savedFlash: boolean;
}

@Component({
	selector: 'app-translation-zen-mode',
	templateUrl: './translation-zen-mode.component.html',
	styleUrl: './translation-zen-mode.component.scss'
})
export class TranslationZenModeComponent implements OnInit, OnDestroy {
	readonly pageSize = 25;
	readonly statusRadioOrder = TRANSLATION_STATUS_RADIO_ORDER;
	readonly translationStatusRadioLabel = translationStatusRadioLabel;

	edition = '';
	refset = '';
	label = '';
	dialectDisplayName = 'Translation';
	pageIndex = 0;
	totalCount: number | null = null;
	statusFilter: string | null = null;
	englishSearch: string | null = null;
	targetSearch: string | null = null;

	loading = false;
	units: ZenUnitState[] = [];

	private snowstormBranchQuery: string | null = null;
	private querySub: Subscription | null = null;
	private pendingSavePromises = new Set<Promise<void>>();

	constructor(
		private fb: FormBuilder,
		private route: ActivatedRoute,
		private router: Router,
		private simplexService: SimplexService,
		private snackBar: MatSnackBar,
		private cdr: ChangeDetectorRef
	) {}

	ngOnInit(): void {
		this.applyRouteParams();
		this.querySub = this.route.queryParamMap.subscribe(() => {
			this.applyRouteParams();
			void this.loadPage();
		});
	}

	ngOnDestroy(): void {
		this.querySub?.unsubscribe();
	}

	get synonymsArray(): (unit: ZenUnitState) => FormArray {
		return (unit) => unit.form.get('synonyms') as FormArray;
	}

	unitSynonymControls(unit: ZenUnitState): FormControl<string>[] {
		return (unit.form.get('synonyms') as FormArray).controls as FormControl<string>[];
	}

	positionLabel(): string {
		if (this.totalCount == null || this.totalCount === 0) {
			return '';
		}
		const start = this.pageIndex * this.pageSize + 1;
		const end = Math.min((this.pageIndex + 1) * this.pageSize, this.totalCount);
		return `Units ${start}–${end} of ${this.totalCount.toLocaleString()}`;
	}

	statusFilterLabel(): string {
		return this.statusFilter ? translationStatusRadioLabel(this.statusFilter) : '';
	}

	hasStatusFilter(): boolean {
		return this.statusFilter != null;
	}

	async clearStatusFilter(): Promise<void> {
		if (!this.statusFilter || this.loading) {
			return;
		}
		await this.navigateToPage(0, null);
	}

	canGoPrev(): boolean {
		return this.pageIndex > 0;
	}

	canGoNext(): boolean {
		return this.totalCount != null && (this.pageIndex + 1) * this.pageSize < this.totalCount;
	}

	isCompleteStatusReadOnly(unit: ZenUnitState): boolean {
		const statusCtrl = unit.form.get('status');
		return statusCtrl?.value === 'COMPLETE' && statusCtrl.disabled;
	}

	goBack(): void {
		void this.router.navigate(
			['/translation-studio', this.edition, this.refset, this.label],
			{ queryParams: mergeTranslationStudioQueryParams({}, this.statusFilter, this.englishSearch, this.targetSearch) }
		);
	}

	async goPrevPage(): Promise<void> {
		if (!this.canGoPrev() || this.loading) {
			return;
		}
		await this.navigateToPage(this.pageIndex - 1);
	}

	async goNextPage(): Promise<void> {
		if (!this.canGoNext() || this.loading) {
			return;
		}
		await this.navigateToPage(this.pageIndex + 1);
	}

	onTermBlur(unit: ZenUnitState): void {
		void this.saveUnitIfChanged(unit);
	}

	onStatusChange(unit: ZenUnitState): void {
		void this.saveUnitIfChanged(unit, true);
	}

	addSynonym(unit: ZenUnitState): void {
		this.synonymsArray(unit).push(this.fb.control('', { nonNullable: true }));
		this.syncUnitStatus(unit);
	}

	removeSynonym(unit: ZenUnitState, index: number): void {
		this.synonymsArray(unit).removeAt(index);
		this.syncUnitStatus(unit);
		void this.saveUnitIfChanged(unit, true);
	}

	makeSynonymPreferredTerm(unit: ZenUnitState, index: number): void {
		const primaryCtrl = unit.form.get('primaryTerm');
		const synCtrl = this.synonymsArray(unit).at(index);
		if (!primaryCtrl || !synCtrl) {
			return;
		}
		const pt = (primaryCtrl.value as string) ?? '';
		const syn = (synCtrl.value as string) ?? '';
		primaryCtrl.setValue(syn);
		synCtrl.setValue(pt);
		this.syncUnitStatus(unit);
		void this.saveUnitIfChanged(unit, true);
	}

	onUnitFormInput(unit: ZenUnitState): void {
		this.syncUnitStatus(unit);
	}

	private applyRouteParams(): void {
		const params = this.route.snapshot.paramMap;
		this.edition = params.get('edition') ?? '';
		this.refset = params.get('refset') ?? '';
		this.label = params.get('label') ?? '';
		const q = this.route.snapshot.queryParamMap;
		this.pageIndex = Math.max(0, parseInt(q.get('page') ?? '0', 10));
		const dParam = q.get('d')?.trim();
		this.dialectDisplayName = dParam && dParam.length > 0 ? dParam : 'Translation';
		const t = q.get('t');
		const parsedT = t != null ? parseInt(t, 10) : NaN;
		this.totalCount = Number.isFinite(parsedT) ? parsedT : null;
		const bq = q.get('b')?.trim();
		this.snowstormBranchQuery = bq && bq.length > 0 ? bq : null;
		this.statusFilter = parseTranslationStatusFilter(q);
		this.englishSearch = parseTranslationEnglishSearch(q);
		this.targetSearch = parseTranslationTargetSearch(q);
	}

	private async loadPage(): Promise<void> {
		if (!this.edition || !this.refset || !this.label) {
			this.snackBar.open('Missing route parameters.', 'Dismiss', { duration: 5000 });
			return;
		}
		this.loading = true;
		this.units = [];
		this.cdr.detectChanges();
		try {
			const resp = await firstValueFrom(
				this.simplexService.getTranslationSetRows(
					this.edition,
					this.refset,
					this.label,
					this.pageIndex,
					this.pageSize,
					this.statusFilter,
					this.englishSearch,
					this.targetSearch
				)
			);
			this.totalCount = resp.count ?? 0;
			const results = resp.results ?? [];
			this.units = results.map((row: any) => this.buildUnitState(row));
		} catch {
			this.snackBar.open('Failed to load translation units.', 'Dismiss', { duration: 6000 });
		} finally {
			this.loading = false;
			this.cdr.detectChanges();
		}
	}

	private buildUnitState(row: any): ZenUnitState {
		const target: string[] = Array.isArray(row.target) ? row.target : [];
		const primary = target[0] ?? '';
		const synonyms = this.fb.array<FormControl<string>>([]);
		for (let i = 1; i < target.length; i++) {
			synonyms.push(this.fb.control(target[i] ?? '', { nonNullable: true }));
		}
		const status = row.status ?? 'NOT_STARTED';
		const form = this.fb.group({
			primaryTerm: [primary],
			status: [status, Validators.required],
			synonyms
		});
		const unit: ZenUnitState = {
			context: row.context != null ? String(row.context) : '',
			sourceTerm: row.source?.[0] ?? '',
			form,
			loadedTargetTerms: normalizeTargetTerms(target),
			loadedStatus: status,
			saving: false,
			saveError: null,
			savedFlash: false
		};
		this.syncUnitStatus(unit);
		return unit;
	}

	private primaryTermValue(unit: ZenUnitState): string {
		return (unit.form.get('primaryTerm')?.value as string) ?? '';
	}

	private synonymValues(unit: ZenUnitState): string[] {
		return this.synonymsArray(unit).controls.map((c) => ((c.value ?? '') as string));
	}

	private syncUnitStatus(unit: ZenUnitState): void {
		const statusCtrl = unit.form.get('status');
		if (!statusCtrl) {
			return;
		}
		const result = syncStatusWithTranslationTextHelper(
			statusCtrl.value as string,
			this.primaryTermValue(unit),
			this.synonymValues(unit),
			unit.loadedTargetTerms
		);
		statusCtrl.patchValue(result.status, { emitEvent: false });
		if (result.disabled && statusCtrl.enabled) {
			statusCtrl.disable({ emitEvent: false });
		} else if (!result.disabled && statusCtrl.disabled) {
			statusCtrl.enable({ emitEvent: false });
		}
	}

	private buildUnitUpdateBody(unit: ZenUnitState): TranslationUpdateBody {
		return buildUpdateBody(
			this.primaryTermValue(unit),
			this.synonymValues(unit),
			(unit.form.getRawValue().status as string) ?? 'FOR_REVIEW'
		);
	}

	private bodyChangedFromLoaded(unit: ZenUnitState, body: TranslationUpdateBody): boolean {
		if (termsChangedFromLoaded(body.terms, unit.loadedTargetTerms)) {
			return true;
		}
		return body.status !== unit.loadedStatus;
	}

	private async saveUnitIfChanged(unit: ZenUnitState, force = false): Promise<void> {
		if (unit.saving) {
			return;
		}
		this.syncUnitStatus(unit);
		const body = this.buildUnitUpdateBody(unit);
		if (!force && !this.bodyChangedFromLoaded(unit, body)) {
			return;
		}
		const savePromise = this.performSave(unit, body);
		this.pendingSavePromises.add(savePromise);
		try {
			await savePromise;
		} finally {
			this.pendingSavePromises.delete(savePromise);
		}
	}

	private async performSave(unit: ZenUnitState, body: TranslationUpdateBody): Promise<void> {
		if (!unit.context) {
			return;
		}
		unit.saving = true;
		unit.saveError = null;
		unit.savedFlash = false;
		this.cdr.detectChanges();
		try {
			await firstValueFrom(
				this.simplexService.updateTranslationUnit(
					this.edition,
					this.refset,
					this.label,
					unit.context,
					body
				)
			);
			unit.loadedTargetTerms = [...body.terms];
			unit.loadedStatus = body.status;
			unit.savedFlash = true;
			this.cdr.detectChanges();
			setTimeout(() => {
				unit.savedFlash = false;
				this.cdr.detectChanges();
			}, 2000);
		} catch {
			unit.saveError = 'Save failed';
		} finally {
			unit.saving = false;
			this.cdr.detectChanges();
		}
	}

	private async flushPendingSaves(): Promise<void> {
		const active = document.activeElement as HTMLElement | null;
		active?.blur();
		await new Promise((resolve) => setTimeout(resolve, 0));
		if (this.pendingSavePromises.size > 0) {
			await Promise.all([...this.pendingSavePromises]);
		}
	}

	private async navigateToPage(newPage: number, statusOverride?: string | null): Promise<void> {
		await this.flushPendingSaves();
		const status = statusOverride !== undefined ? statusOverride : this.statusFilter;
		const queryParams = mergeTranslationStudioQueryParams(
			{
				page: newPage,
				...(statusOverride === undefined && this.totalCount != null ? { t: this.totalCount } : {}),
				...(this.dialectDisplayName !== 'Translation' ? { d: this.dialectDisplayName } : {}),
				...(this.snowstormBranchQuery ? { b: this.snowstormBranchQuery } : {})
			},
			status,
			this.englishSearch,
			this.targetSearch
		);
		await this.router.navigate(
			['/translation-studio', this.edition, this.refset, this.label, 'zen'],
			{ queryParams }
		);
	}
}
