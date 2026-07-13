import {
	buildHierarchyDisplayRows,
	buildPartialHierarchyNodeMap,
	buildPartialHierarchyRows,
	collectAncestors,
	derivePageOrderKey,
	depthFromVisibleParents,
	depthRelativeToAnchor,
	findDeepestCommonAncestor,
	findDeepestPairwiseSharedAncestor,
	nodesForCollapsedView,
	nodesForFullView,
	PartialHierarchyNode,
	PartialHierarchyRow,
	sortCollapsedHierarchyRows
} from './partial-hierarchy.helper';
import REGRESSION_FIXTURE from './fixtures/partial-hierarchy-regression.json';

/** Trimmed subtree from the Body structure hierarchy (ovary vs prostate branches). */
const FIXTURE: PartialHierarchyNode[] = [
	{ code: '138875005', parents: [], term: 'SNOMED CT Concept' },
	{ code: '123037004', parents: ['138875005'], term: 'Body structure' },
	{ code: '91723000', parents: ['442083009'], term: 'Anatomical structure' },
	{ code: '113343008', parents: ['91723000'], term: 'Body organ structure' },
	{ code: '43174007', parents: ['71934003', '113343008', '422697005'], term: 'Gonadal structure' },
	{ code: '15497006', parents: ['27436002', '43174007', '113331007', '302696005', '413344004', '699886009', '818972007'], term: 'Ovarian structure' },
	{ code: 'OVARY_MID', parents: ['15497006'], term: 'Ovarian subdivision' },
	{ code: '43981004', parents: ['OVARY_MID', '110635008', '1052333006'], term: 'Structure of left ovary' },
	{ code: '20837000', parents: ['15497006', '110634007', '1052334000'], term: 'Structure of right ovary' },
	{ code: '306717007', parents: ['113343008'], term: 'Prostatic and/or seminal vesicle structures' },
	{ code: '41216001', parents: ['306717007', '359989001'], term: 'Prostate' }
];

/** On-page concept ids for the body-structure regression page (matches user-reported scenario). */
const REGRESSION_PAGE_IDS = [
	'67923007', // Hypothalamic structure
	'181125003', // Entire pituitary gland
	'71854001', // Colon structure
	'80248007', // Left breast structure
	'73056007', // Right breast structure
	'44029006', // Left lung structure
	'3341006', // Right lung structure
	'64033007', // Kidney structure
	'18639004', // Left kidney structure
	'9846003', // Right kidney structure
	'89837001', // Urinary bladder structure
	'76784001', // Vaginal structure
	'18911002', // Penile structure
	'41216001', // Prostate
	'40689003', // Testis structure
	'786841006', // Structure of all fingers of left hand
	'786842004', // Structure of all fingers of right hand
	'43981004', // Structure of left ovary
	'20837000', // Structure of right ovary
	'71252005' // Cervix uteri structure
];

function visualParentCode(rows: PartialHierarchyRow[], index: number): string | null {
	const depth = rows[index].depth;
	if (depth === 0) {
		return null;
	}
	for (let i = index - 1; i >= 0; i--) {
		if (rows[i].depth === depth - 1) {
			return rows[i].code;
		}
	}
	return null;
}

function isVisibleGraphParent(
	child: string,
	parent: string,
	visibleCodes: Set<string>,
	nodeMap: Map<string, PartialHierarchyNode>
): boolean {
	const visited = new Set<string>();
	const stack = [child];
	while (stack.length > 0) {
		const c = stack.pop()!;
		if (visited.has(c)) {
			continue;
		}
		visited.add(c);
		const parents = nodeMap.get(c)?.parents ?? [];
		if (parents.includes(parent) && visibleCodes.has(parent)) {
			return true;
		}
		for (const p of parents) {
			if (!visited.has(p)) {
				stack.push(p);
			}
		}
	}
	return false;
}

describe('partial-hierarchy.helper', () => {
	const nodeMap = () => buildPartialHierarchyNodeMap(FIXTURE);
	const pageIds = new Set(['43981004', '20837000']);
	const orderInPage = new Map([
		['43981004', 0],
		['20837000', 1]
	]);

	it('findDeepestCommonAncestor returns the most specific shared ancestor', () => {
		expect(findDeepestCommonAncestor(pageIds, nodeMap())).toBe('15497006');
	});

	it('findDeepestCommonAncestor returns null when there is no shared ancestor', () => {
		const disjointMap = buildPartialHierarchyNodeMap([
			{ code: 'A', parents: [], term: 'A' },
			{ code: 'B', parents: [], term: 'B' }
		]);
		expect(findDeepestCommonAncestor(new Set(['A', 'B']), disjointMap)).toBeNull();
	});

	it('nodesForCollapsedView returns DCA and page concepts only when pairwise equals DCA', () => {
		const dca = '15497006';
		const nodes = nodesForCollapsedView(FIXTURE, pageIds, dca);
		expect(nodes.map((n) => n.code).sort()).toEqual(['15497006', '20837000', '43981004']);
	});

	it('nodesForCollapsedView adds deeper pairwise group ancestors when global DCA is shallower', () => {
		const mixedPageIds = new Set(['43981004', '20837000', '41216001']);
		const map = nodeMap();
		const globalDca = findDeepestCommonAncestor(mixedPageIds, map);
		expect(globalDca).toBe('113343008');
		expect(findDeepestPairwiseSharedAncestor('43981004', mixedPageIds, map)).toBe('15497006');
		expect(findDeepestPairwiseSharedAncestor('41216001', mixedPageIds, map)).toBe('113343008');
		const nodes = nodesForCollapsedView(FIXTURE, mixedPageIds, globalDca);
		const codes = nodes.map((n) => n.code);
		expect(codes).toContain('113343008');
		expect(codes).toContain('15497006');
		expect(codes).toContain('43981004');
		expect(codes).toContain('20837000');
		expect(codes).toContain('41216001');
	});

	it('derivePageOrderKey uses minimum orderInPage among on-page descendants', () => {
		const map = nodeMap();
		expect(derivePageOrderKey('43981004', pageIds, orderInPage, map)).toBe(0);
		expect(derivePageOrderKey('15497006', pageIds, orderInPage, map)).toBe(0);
		expect(derivePageOrderKey('41216001', new Set(['41216001']), new Map([['41216001', 5]]), map)).toBe(5);
	});

	it('buildHierarchyDisplayRows interleaves group ancestors directly above their page descendants', () => {
		const mixedPageIds = new Set(['43981004', '20837000', '41216001']);
		const order = new Map([
			['43981004', 0],
			['20837000', 1],
			['41216001', 2]
		]);
		const rows = buildHierarchyDisplayRows(FIXTURE, mixedPageIds, order, false);
		expect(rows.map((r) => r.code)).toEqual([
			'138875005',
			'123037004',
			'91723000',
			'113343008',
			'15497006',
			'43981004',
			'20837000',
			'41216001'
		]);
		expect(rows[0].depth).toBe(0);
		const ovarian = rows.find((r) => r.code === '15497006')!;
		const left = rows.find((r) => r.code === '43981004')!;
		const right = rows.find((r) => r.code === '20837000')!;
		const prostate = rows.find((r) => r.code === '41216001')!;
		expect(ovarian.depth).toBe(4);
		expect(left.depth).toBe(ovarian.depth + 1);
		expect(right.depth).toBe(ovarian.depth + 1);
		expect(prostate.depth).toBe(4);
		expect(rows.indexOf(ovarian)).toBeLessThan(rows.indexOf(left));
		expect(rows.indexOf(left) + 1).toBe(rows.indexOf(right));
		expect(rows.indexOf(right)).toBeLessThan(rows.indexOf(prostate));
	});

	it('buildHierarchyDisplayRows orders branches by orderInPage when prostate is first', () => {
		const mixedPageIds = new Set(['43981004', '20837000', '41216001']);
		const order = new Map([
			['41216001', 0],
			['43981004', 1],
			['20837000', 2]
		]);
		const rows = buildHierarchyDisplayRows(FIXTURE, mixedPageIds, order, false);
		expect(rows.map((r) => r.code)).toEqual([
			'138875005',
			'123037004',
			'91723000',
			'113343008',
			'41216001',
			'15497006',
			'43981004',
			'20837000'
		]);
	});

	it('buildHierarchyDisplayRows keeps on-page concepts in orderInPage order in full view', () => {
		const mixedPageIds = new Set(['43981004', '20837000', '41216001']);
		const order = new Map([
			['41216001', 0],
			['43981004', 1],
			['20837000', 2]
		]);
		const rows = buildHierarchyDisplayRows(FIXTURE, mixedPageIds, order, true);
		const onPageCodes = rows.filter((r) => r.onCurrentPage).map((r) => r.code);
		expect(onPageCodes).toEqual(['41216001', '43981004', '20837000']);
		expect(rows.findIndex((r) => r.code === '41216001')).toBeLessThan(
			rows.findIndex((r) => r.code === '43981004')
		);
		expect(rows.findIndex((r) => r.code === '306717007')).toBeLessThan(
			rows.findIndex((r) => r.code === '43174007')
		);
	});

	it('compact view reflects swapped orderInPage for left and right ovary', () => {
		const swappedOrder = new Map([
			['20837000', 0],
			['43981004', 1]
		]);
		const rows = buildHierarchyDisplayRows(FIXTURE, pageIds, swappedOrder, false);
		const onPage = rows.filter((r) => r.onCurrentPage).map((r) => r.code);
		expect(onPage).toEqual(['20837000', '43981004']);
	});

	it('nodesForFullView excludes off-page children of on-page concepts that are not path connectors', () => {
		const pageIdsWithParent = new Set(['15497006', '43981004']);
		const dca = '15497006';
		const nodes = nodesForFullView(FIXTURE, pageIdsWithParent, dca);
		const codes = nodes.map((n) => n.code);
		expect(codes).toContain('15497006');
		expect(codes).toContain('43981004');
		expect(codes).toContain('OVARY_MID');
		expect(codes).not.toContain('20837000');
	});

	it('nodesForFullView excludes off-page descendants when only the parent is on-page', () => {
		const pageIdsParentOnly = new Set(['15497006']);
		const dca = findDeepestCommonAncestor(pageIdsParentOnly, nodeMap())!;
		const nodes = nodesForFullView(FIXTURE, pageIdsParentOnly, dca);
		const codes = nodes.map((n) => n.code);
		expect(codes).toEqual([
			'138875005',
			'123037004',
			'91723000',
			'113343008',
			'43174007',
			'15497006'
		]);
	});

	it('nodesForFullView includes on-path intermediates and excludes sibling branches', () => {
		const dca = '15497006';
		const nodes = nodesForFullView(FIXTURE, pageIds, dca);
		const codes = nodes.map((n) => n.code);
		expect(codes).toContain('15497006');
		expect(codes).toContain('OVARY_MID');
		expect(codes).toContain('43981004');
		expect(codes).toContain('20837000');
		expect(codes).not.toContain('43174007');
		expect(codes).not.toContain('41216001');
		expect(codes).toContain('138875005');
	});

	it('collapsed view indents from visible parents only; full view uses full graph depth', () => {
		const dca = '15497006';
		const map = nodeMap();
		const collapsed = nodesForCollapsedView(FIXTURE, pageIds, dca);
		const full = nodesForFullView(FIXTURE, pageIds, dca);
		const collapsedRows = buildPartialHierarchyRows(collapsed, pageIds, map, dca, true);
		const fullRows = buildPartialHierarchyRows(full, pageIds, map, dca, false);
		const leftCollapsed = collapsedRows.find((r) => r.code === '43981004')!;
		const leftFull = fullRows.find((r) => r.code === '43981004')!;
		expect(leftCollapsed.depth).toBe(1);
		expect(leftFull.depth).toBe(2);
		expect(depthFromVisibleParents('43981004', collapsed, dca, map)).toBe(1);
		expect(depthRelativeToAnchor('43981004', dca, map)).toBe(2);
		expect(depthRelativeToAnchor('15497006', dca, map)).toBe(0);
	});

	it('buildHierarchyDisplayRows sorts collapsed view with DCA first then page order', () => {
		const rows = buildHierarchyDisplayRows(FIXTURE, pageIds, orderInPage, false);
		expect(rows[0].code).toBe('138875005');
		expect(rows[0].onCurrentPage).toBe(false);
		expect(rows.find((r) => r.code === '15497006')!.code).toBe('15497006');
		expect(rows.findIndex((r) => r.code === '43981004')).toBeGreaterThan(
			rows.findIndex((r) => r.code === '15497006')
		);
		expect(rows.findIndex((r) => r.code === '20837000')).toBeGreaterThan(
			rows.findIndex((r) => r.code === '43981004')
		);
	});

	it('collectAncestors walks multiple parent links', () => {
		const ancestors = collectAncestors('43981004', nodeMap());
		expect(ancestors.has('15497006')).toBe(true);
		expect(ancestors.has('43174007')).toBe(true);
	});

	it('sortCollapsedHierarchyRows places DCA before page concepts', () => {
		const dca = '15497006';
		const rows = buildPartialHierarchyRows(
			nodesForCollapsedView(FIXTURE, pageIds, dca),
			pageIds,
			nodeMap(),
			dca,
			true
		);
		const sorted = sortCollapsedHierarchyRows(rows, dca, orderInPage, FIXTURE, pageIds, nodeMap());
		expect(sorted.map((r) => r.code)).toEqual([
			'138875005',
			'123037004',
			'91723000',
			'113343008',
			'43174007',
			'15497006',
			'43981004',
			'20837000'
		]);
	});

	describe('partial-hierarchy regression (user-reported false nesting)', () => {
		const regressionPartial = REGRESSION_FIXTURE as PartialHierarchyNode[];
		const regressionPageIds = new Set(REGRESSION_PAGE_IDS);
		const regressionOrder = new Map(REGRESSION_PAGE_IDS.map((id, i) => [id, i]));
		const regressionNodeMap = () => buildPartialHierarchyNodeMap(regressionPartial);

		it('does not nest Colon structure under Breast structure in full view', () => {
			const rows = buildHierarchyDisplayRows(
				regressionPartial,
				regressionPageIds,
				regressionOrder,
				true
			);
			const colonIdx = rows.findIndex((r) => r.code === '71854001');
			expect(colonIdx).toBeGreaterThanOrEqual(0);
			const visualParent = visualParentCode(rows, colonIdx);
			expect(visualParent).not.toBe('76752008');
			expect(visualParent).not.toBe('80248007');
			expect(visualParent).not.toBe('73056007');
			expect(visualParent).toBe('1285733009');
		});

		it('does not nest finger structures under Kidney structure in full view', () => {
			const rows = buildHierarchyDisplayRows(
				regressionPartial,
				regressionPageIds,
				regressionOrder,
				true
			);
			const leftFingersIdx = rows.findIndex((r) => r.code === '786841006');
			expect(leftFingersIdx).toBeGreaterThanOrEqual(0);
			const visualParent = visualParentCode(rows, leftFingersIdx);
			expect(visualParent).not.toBe('64033007');
			expect(visualParent).toBe('70327001');
		});

		it('places Structure of all fingers before its digit children', () => {
			const rows = buildHierarchyDisplayRows(
				regressionPartial,
				regressionPageIds,
				regressionOrder,
				true
			);
			const allFingersIdx = rows.findIndex((r) => r.code === '70327001');
			const leftFingersIdx = rows.findIndex((r) => r.code === '786841006');
			const rightFingersIdx = rows.findIndex((r) => r.code === '786842004');
			expect(allFingersIdx).toBeGreaterThanOrEqual(0);
			expect(allFingersIdx).toBeLessThan(leftFingersIdx);
			expect(allFingersIdx).toBeLessThan(rightFingersIdx);
		});

		it('satisfies visual-parent graph invariant for every row in full view', () => {
			const rows = buildHierarchyDisplayRows(
				regressionPartial,
				regressionPageIds,
				regressionOrder,
				true
			);
			const visibleCodes = new Set(rows.map((r) => r.code));
			const map = regressionNodeMap();
			for (let i = 0; i < rows.length; i++) {
				if (rows[i].depth === 0) {
					continue;
				}
				const parent = visualParentCode(rows, i);
				expect(parent).withContext(`row ${rows[i].term}`).not.toBeNull();
				expect(isVisibleGraphParent(rows[i].code, parent!, visibleCodes, map))
					.withContext(`${rows[i].term} under ${map.get(parent!)?.term}`)
					.toBe(true);
			}
		});

		it('does not leave compact-view page concepts as depth-0 orphans', () => {
			const rows = buildHierarchyDisplayRows(
				regressionPartial,
				regressionPageIds,
				regressionOrder,
				false
			);
			const dca = findDeepestCommonAncestor(regressionPageIds, regressionNodeMap());
			const orphanPageConcepts = rows.filter(
				(r) => r.onCurrentPage && r.depth === 0 && r.code !== dca
			);
			expect(orphanPageConcepts.map((r) => r.code)).toEqual([]);
		});

		it('nests Colon structure under its compact effective parent', () => {
			const rows = buildHierarchyDisplayRows(
				regressionPartial,
				regressionPageIds,
				regressionOrder,
				false
			);
			const colonIdx = rows.findIndex((r) => r.code === '71854001');
			expect(colonIdx).toBeGreaterThanOrEqual(0);
			expect(rows[colonIdx].depth).toBeGreaterThan(0);
			expect(visualParentCode(rows, colonIdx)).toBe('818993005');
		});

		it('nests Prostate and Penile structure under their compact effective parents', () => {
			const rows = buildHierarchyDisplayRows(
				regressionPartial,
				regressionPageIds,
				regressionOrder,
				false
			);
			const penileIdx = rows.findIndex((r) => r.code === '18911002');
			const prostateIdx = rows.findIndex((r) => r.code === '41216001');
			expect(visualParentCode(rows, penileIdx)).toBe('127903009');
			expect(visualParentCode(rows, prostateIdx)).toBe('38242008');
		});

		it('uses Anatomical or acquired body structure as DCA for the regression page', () => {
			expect(findDeepestCommonAncestor(regressionPageIds, regressionNodeMap(), regressionPartial)).toBe(
				'442083009'
			);
		});

		it('nests Diencephalon part under Anatomical or acquired body structure in compact view', () => {
			const rows = buildHierarchyDisplayRows(
				regressionPartial,
				regressionPageIds,
				regressionOrder,
				false
			);
			expect(rows[0].code).toBe('138875005');
			const diencephalonIdx = rows.findIndex((r) => r.code === '119264001');
			expect(diencephalonIdx).toBeGreaterThanOrEqual(0);
			expect(rows[diencephalonIdx].depth).toBe(3);
			expect(visualParentCode(rows, diencephalonIdx)).toBe('442083009');
		});

		it('keeps Anatomical or acquired body structure as DCA when it is on the page', () => {
			const pageIds = new Set(['442083009', ...REGRESSION_PAGE_IDS]);
			const order = new Map(['442083009', ...REGRESSION_PAGE_IDS].map((id, i) => [id, i]));
			expect(findDeepestCommonAncestor(pageIds, regressionNodeMap(), regressionPartial)).toBe(
				'442083009'
			);
			const rows = buildHierarchyDisplayRows(regressionPartial, pageIds, order, false);
			expect(rows[0].code).toBe('138875005');
			expect(rows.find((r) => r.code === '442083009')!.onCurrentPage).toBe(true);
			const diencephalonIdx = rows.findIndex((r) => r.code === '119264001');
			expect(visualParentCode(rows, diencephalonIdx)).toBe('442083009');
			expect(rows.findIndex((r) => r.code === '123037004')).toBeGreaterThanOrEqual(0);
		});

		it('always includes the hierarchy root in compact view', () => {
			const rows = buildHierarchyDisplayRows(
				regressionPartial,
				regressionPageIds,
				regressionOrder,
				false
			);
			expect(rows[0].code).toBe('138875005');
			expect(rows[0].term).toBe('SNOMED CT Concept');
			expect(rows.findIndex((r) => r.code === '123037004')).toBeGreaterThanOrEqual(0);
		});
	});
});
