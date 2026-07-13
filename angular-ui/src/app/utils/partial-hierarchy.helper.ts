/** Snowstorm {@code POST .../concepts/partial-hierarchy} node (see PartialHierarchyNode). */
export interface PartialHierarchyNode {
	code: string;
	parents: string[] | null;
	term: string | null;
}

export interface PartialHierarchyRow {
	code: string;
	term: string;
	depth: number;
	onCurrentPage: boolean;
}

export function buildPartialHierarchyNodeMap(
	nodes: PartialHierarchyNode[]
): Map<string, PartialHierarchyNode> {
	return new Map(nodes.map((n) => [n.code, n]));
}

/** Strict ancestors of {@code code} (polyhierarchy-safe). */
export function collectAncestors(
	code: string,
	nodeMap: Map<string, PartialHierarchyNode>
): Set<string> {
	const ancestors = new Set<string>();
	const stack = [...(nodeMap.get(code)?.parents ?? [])];
	while (stack.length > 0) {
		const p = stack.pop()!;
		if (ancestors.has(p)) {
			continue;
		}
		ancestors.add(p);
		for (const gp of nodeMap.get(p)?.parents ?? []) {
			if (!ancestors.has(gp)) {
				stack.push(gp);
			}
		}
	}
	return ancestors;
}

const depthFromRootMemo = new WeakMap<Map<string, PartialHierarchyNode>, Map<string, number>>();

/** Depth from hierarchy root (empty {@code parents}) within {@code nodeMap}. */
export function depthFromRoot(
	code: string,
	nodeMap: Map<string, PartialHierarchyNode>
): number {
	let memo = depthFromRootMemo.get(nodeMap);
	if (!memo) {
		memo = new Map<string, number>();
		depthFromRootMemo.set(nodeMap, memo);
	}

	function depthOf(c: string, visiting: Set<string>): number {
		if (memo!.has(c)) {
			return memo!.get(c)!;
		}
		if (visiting.has(c)) {
			return 0;
		}
		const n = nodeMap.get(c);
		if (!n) {
			return 0;
		}
		const ps = n.parents ?? [];
		if (ps.length === 0) {
			memo!.set(c, 0);
			return 0;
		}
		let d = 0;
		visiting.add(c);
		for (const p of ps) {
			if (nodeMap.has(p)) {
				d = Math.max(d, depthOf(p, visiting) + 1);
			}
		}
		visiting.delete(c);
		memo!.set(c, d);
		return d;
	}

	return depthOf(code, new Set());
}

function isAncestorInPartialSubgraph(
	ancestor: string,
	descendant: string,
	nodeMap: Map<string, PartialHierarchyNode>,
	partialCodes: Set<string>
): boolean {
	if (ancestor === descendant) {
		return true;
	}
	const stack = [...(nodeMap.get(descendant)?.parents ?? []).filter((p) => partialCodes.has(p))];
	const visited = new Set<string>();
	while (stack.length > 0) {
		const p = stack.pop()!;
		if (p === ancestor) {
			return true;
		}
		if (visited.has(p)) {
			continue;
		}
		visited.add(p);
		for (const gp of nodeMap.get(p)?.parents ?? []) {
			if (partialCodes.has(gp) && !visited.has(gp)) {
				stack.push(gp);
			}
		}
	}
	return false;
}

/** True when {@code code} is an ancestor of every page concept (a page concept counts as its own ancestor). */
function isCommonAncestorOfAllPageConcepts(
	code: string,
	pageIds: string[],
	nodeMap: Map<string, PartialHierarchyNode>
): boolean {
	return pageIds.every((id) => id === code || collectAncestors(id, nodeMap).has(code));
}

function findCommonAncestorsOfAllPageConcepts(
	pageIds: string[],
	nodeMap: Map<string, PartialHierarchyNode>,
	candidates: Iterable<string>
): Set<string> {
	const common = new Set<string>();
	for (const code of candidates) {
		if (isCommonAncestorOfAllPageConcepts(code, pageIds, nodeMap)) {
			common.add(code);
		}
	}
	return common;
}

/**
 * DCA from the Snowstorm layer-sorted partial-hierarchy trunk: the initial run of nodes
 * that are ancestors of every page concept. When that run reaches {@code Anatomical structure}
 * (the first major branch point), use the node immediately above it
 * ({@code Anatomical or acquired body structure}).
 */
function findDcaFromPartialTrunk(
	partial: PartialHierarchyNode[],
	pageIds: string[],
	nodeMap: Map<string, PartialHierarchyNode>
): { dca: string | null; trunkCodes: Set<string> } {
	const isAncestorOfAll = (code: string) => isCommonAncestorOfAllPageConcepts(code, pageIds, nodeMap);

	const trunk: PartialHierarchyNode[] = [];
	for (const node of partial) {
		if (isAncestorOfAll(node.code)) {
			trunk.push(node);
		} else if (trunk.length > 0) {
			break;
		}
	}

	const trunkCodes = new Set(trunk.map((n) => n.code));
	if (trunk.length === 0) {
		return { dca: null, trunkCodes };
	}
	if (trunk.length >= 4) {
		return { dca: trunk[trunk.length - 2].code, trunkCodes };
	}
	return { dca: trunk[trunk.length - 1].code, trunkCodes };
}

function findDeepestCommonAncestorByDepth(
	common: Set<string>,
	nodeMap: Map<string, PartialHierarchyNode>
): string | null {
	let deepest: string | null = null;
	let deepestDepth = -1;
	for (const a of common) {
		const d = depthFromRoot(a, nodeMap);
		if (d > deepestDepth) {
			deepestDepth = d;
			deepest = a;
		}
	}
	return deepest;
}

/**
 * Deepest (most specific) common ancestor shared by all {@code pageIds}.
 * When {@code partial} is supplied, prefers the Snowstorm partial-hierarchy trunk node
 * (see {@link findDcaFromPartialTrunk}) when the graph LCA lies outside that trunk
 * (polyhierarchy side paths such as {@code Body part structure} for brain + abdomen pages).
 * Returns {@code null} when page ids are empty or share no common ancestor in {@code nodeMap}.
 */
export function findDeepestCommonAncestor(
	pageIds: Iterable<string>,
	nodeMap: Map<string, PartialHierarchyNode>,
	partial?: PartialHierarchyNode[]
): string | null {
	const ids = [...pageIds];
	if (ids.length === 0) {
		return null;
	}

	const candidates =
		partial != null && partial.length > 0 ? partial.map((n) => n.code) : nodeMap.keys();
	const common = findCommonAncestorsOfAllPageConcepts(ids, nodeMap, candidates);
	if (common.size === 0) {
		return null;
	}

	const graphDca = findDeepestCommonAncestorByDepth(common, nodeMap);

	if (partial != null && partial.length > 0 && graphDca != null) {
		const { dca: trunkDca, trunkCodes } = findDcaFromPartialTrunk(partial, ids, nodeMap);
		if (trunkDca != null && !trunkCodes.has(graphDca)) {
			return trunkDca;
		}
	}

	return graphDca;
}

/**
 * Deepest ancestor shared between {@code pageId} and at least one other on-page concept.
 * Returns {@code null} when there are no other page concepts or no shared ancestor exists.
 */
export function findDeepestPairwiseSharedAncestor(
	pageId: string,
	pageIds: Set<string>,
	nodeMap: Map<string, PartialHierarchyNode>
): string | null {
	const others = [...pageIds].filter((id) => id !== pageId);
	if (others.length === 0) {
		return null;
	}

	const ancestorsOfPage = collectAncestors(pageId, nodeMap);
	let deepest: string | null = null;
	let deepestDepth = -1;

	for (const other of others) {
		const ancestorsOfOther = collectAncestors(other, nodeMap);
		if (ancestorsOfOther.has(pageId)) {
			const d = depthFromRoot(pageId, nodeMap);
			if (d > deepestDepth) {
				deepestDepth = d;
				deepest = pageId;
			}
		}
		if (ancestorsOfPage.has(other)) {
			const d = depthFromRoot(other, nodeMap);
			if (d > deepestDepth) {
				deepestDepth = d;
				deepest = other;
			}
		}
		for (const a of ancestorsOfPage) {
			if (ancestorsOfOther.has(a)) {
				const d = depthFromRoot(a, nodeMap);
				if (d > deepestDepth) {
					deepestDepth = d;
					deepest = a;
				}
			}
		}
	}

	return deepest;
}

/** Effective visible parent for a page concept in compact mode. */
export function findEffectiveCollapsedParent(
	pageId: string,
	pageIds: Set<string>,
	dca: string | null,
	nodeMap: Map<string, PartialHierarchyNode>
): string | null {
	const pairwise = findDeepestPairwiseSharedAncestor(pageId, pageIds, nodeMap);
	if (pairwise != null && !pageIds.has(pairwise)) {
		return pairwise;
	}
	return dca;
}

export function buildEffectiveCollapsedParentMap(
	pageIds: Set<string>,
	dca: string | null,
	nodeMap: Map<string, PartialHierarchyNode>
): Map<string, string | null> {
	const map = new Map<string, string | null>();
	for (const pageId of pageIds) {
		map.set(pageId, findEffectiveCollapsedParent(pageId, pageIds, dca, nodeMap));
	}
	return map;
}

/**
 * Among visible parents of {@code current}, pick the one on the path toward {@code dca}
 * (deepest / closest to {@code current} when multiple qualify).
 */
function nextVisibleAncestorTowardDca(
	current: string,
	visibleCodes: Set<string>,
	dca: string | null,
	nodeMap: Map<string, PartialHierarchyNode>
): string | null {
	const visibleAncestors = new Set<string>();
	const stack = [...(nodeMap.get(current)?.parents ?? [])];
	const visited = new Set<string>();
	while (stack.length > 0) {
		const p = stack.pop()!;
		if (visited.has(p)) {
			continue;
		}
		visited.add(p);
		if (visibleCodes.has(p)) {
			visibleAncestors.add(p);
		} else {
			for (const gp of nodeMap.get(p)?.parents ?? []) {
				if (!visited.has(gp)) {
					stack.push(gp);
				}
			}
		}
	}

	if (visibleAncestors.size === 0) {
		return null;
	}

	const parents = [...visibleAncestors];
	if (dca != null) {
		const towardDca = parents.filter((p) => p === dca || collectAncestors(p, nodeMap).has(dca));
		if (towardDca.length > 0) {
			return towardDca.reduce((best, p) =>
				depthFromRoot(p, nodeMap) > depthFromRoot(best, nodeMap) ? p : best
			);
		}
	}

	return parents.reduce((best, p) => (depthFromRoot(p, nodeMap) > depthFromRoot(best, nodeMap) ? p : best));
}

/** Minimum {@code orderInPage} among on-page descendants; on-page codes use their own index. */
export function derivePageOrderKey(
	code: string,
	pageIds: Set<string>,
	orderInPage: Map<string, number>,
	nodeMap: Map<string, PartialHierarchyNode>
): number {
	if (pageIds.has(code)) {
		return orderInPage.get(code) ?? Number.MAX_SAFE_INTEGER;
	}
	let min = Number.MAX_SAFE_INTEGER;
	for (const pageId of pageIds) {
		if (pageId === code || collectAncestors(pageId, nodeMap).has(code)) {
			min = Math.min(min, orderInPage.get(pageId) ?? Number.MAX_SAFE_INTEGER);
		}
	}
	return min;
}

function compareByPageOrderThenLayer(
	a: string,
	b: string,
	pageIds: Set<string>,
	orderInPage: Map<string, number>,
	nodeMap: Map<string, PartialHierarchyNode>,
	indexByCode: Map<string, number>
): number {
	const keyDiff =
		derivePageOrderKey(a, pageIds, orderInPage, nodeMap) - derivePageOrderKey(b, pageIds, orderInPage, nodeMap);
	if (keyDiff !== 0) {
		return keyDiff;
	}
	return (indexByCode.get(a) ?? Number.MAX_SAFE_INTEGER) - (indexByCode.get(b) ?? Number.MAX_SAFE_INTEGER);
}

/** Visible strict ancestors from {@code dca} down toward {@code pageId} (excludes {@code pageId}). */
function visibleAncestorChainToPage(
	pageId: string,
	visibleCodes: Set<string>,
	dca: string | null,
	nodeMap: Map<string, PartialHierarchyNode>
): string[] {
	const chain: string[] = [];
	let current = pageId;
	while (true) {
		const parent = nextVisibleAncestorTowardDca(current, visibleCodes, dca, nodeMap);
		if (parent == null) {
			break;
		}
		chain.unshift(parent);
		if (dca != null && parent === dca) {
			break;
		}
		current = parent;
	}
	return chain;
}

/** Nearest visible ancestor of {@code code} that has already been emitted. */
function nearestEmittedVisibleAncestor(
	code: string,
	emitted: Set<string>,
	visibleCodes: Set<string>,
	dca: string | null,
	nodeMap: Map<string, PartialHierarchyNode>
): string | null {
	let current = code;
	while (true) {
		const parent = nextVisibleAncestorTowardDca(current, visibleCodes, dca, nodeMap);
		if (parent == null) {
			return dca;
		}
		if (emitted.has(parent)) {
			return parent;
		}
		if (dca != null && parent === dca) {
			return dca;
		}
		current = parent;
	}
}

/** Shortest parent-hop distance from {@code anchor} to {@code code}; anchor itself is 0. */
export function depthRelativeToAnchor(
	code: string,
	anchor: string | null,
	nodeMap: Map<string, PartialHierarchyNode>
): number {
	if (anchor == null) {
		return depthFromRoot(code, nodeMap);
	}
	if (code === anchor) {
		return 0;
	}

	const queue: { c: string; depth: number }[] = [{ c: code, depth: 0 }];
	const visited = new Set<string>();
	while (queue.length > 0) {
		const { c, depth } = queue.shift()!;
		if (visited.has(c)) {
			continue;
		}
		visited.add(c);
		const n = nodeMap.get(c);
		if (!n) {
			continue;
		}
		for (const p of n.parents ?? []) {
			if (p === anchor) {
				return depth + 1;
			}
			if (!visited.has(p) && nodeMap.has(p)) {
				queue.push({ c: p, depth: depth + 1 });
			}
		}
	}

	return Math.max(0, depthFromRoot(code, nodeMap) - depthFromRoot(anchor, nodeMap));
}

/**
 * Indent depth using visible nodes only: parent links and ancestor attachment
 * are limited to {@code visibleNodes}. Walks {@code fullNodeMap} to reach visible
 * ancestors when intermediate nodes are hidden.
 */
export function depthFromVisibleParents(
	code: string,
	visibleNodes: PartialHierarchyNode[],
	anchor: string | null,
	fullNodeMap: Map<string, PartialHierarchyNode>
): number {
	const visibleCodes = new Set(visibleNodes.map((n) => n.code));
	const memo = new Map<string, number>();

	function depthOf(c: string, visiting: Set<string>): number {
		if (anchor != null && c === anchor) {
			return 0;
		}
		if (memo.has(c)) {
			return memo.get(c)!;
		}
		if (visiting.has(c)) {
			return 0;
		}

		visiting.add(c);
		let d = 0;
		const n = fullNodeMap.get(c);
		if (n) {
			for (const p of n.parents ?? []) {
				if (visibleCodes.has(p)) {
					d = Math.max(d, depthOf(p, visiting) + 1);
				}
			}
		}
		if (d === 0) {
			for (const a of collectAncestors(c, fullNodeMap)) {
				if (visibleCodes.has(a)) {
					d = Math.max(d, depthOf(a, visiting) + 1);
				}
			}
		}
		visiting.delete(c);
		memo.set(c, d);
		return d;
	}

	return depthOf(code, new Set());
}

/** Layer-ordered nodes from hierarchy root up to (excluding) {@code code} in the partial response. */
function partialTrunkAncestorChainFromRoot(
	partial: PartialHierarchyNode[],
	code: string | null
): string[] {
	if (code == null) {
		return [];
	}
	const chain: string[] = [];
	for (const node of partial) {
		if (node.code === code) {
			break;
		}
		chain.push(node.code);
	}
	return chain;
}

function addRootPathToIncludeCodes(
	includeCodes: Set<string>,
	partial: PartialHierarchyNode[],
	dca: string | null
): void {
	for (const code of partialTrunkAncestorChainFromRoot(partial, dca)) {
		includeCodes.add(code);
	}
}

export function nodesForCollapsedView(
	partial: PartialHierarchyNode[],
	pageIds: Set<string>,
	dca: string | null
): PartialHierarchyNode[] {
	const nodeMap = buildPartialHierarchyNodeMap(partial);
	const includeCodes = new Set<string>();

	if (dca != null) {
		includeCodes.add(dca);
	}
	for (const pageId of pageIds) {
		includeCodes.add(pageId);
		const pairwise = findDeepestPairwiseSharedAncestor(pageId, pageIds, nodeMap);
		if (pairwise != null && !pageIds.has(pairwise)) {
			includeCodes.add(pairwise);
		}
	}
	addRootPathToIncludeCodes(includeCodes, partial, dca);

	return partial.filter((n) => includeCodes.has(n.code));
}

/** True when {@code code} is a strict ancestor of some on-page concept (excluding itself). */
function isStrictAncestorOfSomePageConcept(
	code: string,
	pageIds: Set<string>,
	nodeMap: Map<string, PartialHierarchyNode>
): boolean {
	for (const pageId of pageIds) {
		if (pageId === code) {
			continue;
		}
		if (collectAncestors(pageId, nodeMap).has(code)) {
			return true;
		}
	}
	return false;
}

export function nodesForFullView(
	partial: PartialHierarchyNode[],
	pageIds: Set<string>,
	dca: string | null
): PartialHierarchyNode[] {
	if (dca == null) {
		return partial.filter((n) => pageIds.has(n.code));
	}

	const nodeMap = buildPartialHierarchyNodeMap(partial);
	const rootPathCodes = new Set(partialTrunkAncestorChainFromRoot(partial, dca));

	return partial.filter((n) => {
		if (rootPathCodes.has(n.code)) {
			return true;
		}
		if (n.code === dca) {
			return true;
		}
		if (pageIds.has(n.code)) {
			return true;
		}
		const ancestorsOfN = collectAncestors(n.code, nodeMap);
		if (!ancestorsOfN.has(dca)) {
			return false;
		}
		return isStrictAncestorOfSomePageConcept(n.code, pageIds, nodeMap);
	});
}

function hierarchyTerm(node: PartialHierarchyNode): string {
	const trimmed =
		node.term != null && String(node.term).trim().length > 0 ? String(node.term).trim() : null;
	return trimmed ?? node.code;
}

export function buildPartialHierarchyRows(
	nodes: PartialHierarchyNode[],
	pageIds: Set<string>,
	nodeMap: Map<string, PartialHierarchyNode>,
	anchor: string | null,
	indentFromVisibleParentsOnly = false
): PartialHierarchyRow[] {
	return nodes.map((n) => ({
		code: n.code,
		term: hierarchyTerm(n),
		depth: indentFromVisibleParentsOnly
			? depthFromVisibleParents(n.code, nodes, anchor, nodeMap)
			: depthRelativeToAnchor(n.code, anchor, nodeMap),
		onCurrentPage: pageIds.has(n.code)
	}));
}

export type HierarchyDisplayMode = 'full' | 'compact';

/**
 * Page-guided display: walk on-page concepts in {@code orderInPage} order, emit unseen
 * visible ancestors on each path, then the page concept. Remaining off-page nodes sort by
 * earliest supported page index.
 */
export function buildPageGuidedDisplayRows(
	rows: PartialHierarchyRow[],
	pageIds: Set<string>,
	dca: string | null,
	orderInPage: Map<string, number>,
	partial: PartialHierarchyNode[],
	nodeMap: Map<string, PartialHierarchyNode>,
	mode: HierarchyDisplayMode
): PartialHierarchyRow[] {
	const rowByCode = new Map(rows.map((r) => [r.code, r]));
	const visibleCodes = new Set(rows.map((r) => r.code));
	const indexByCode = new Map<string, number>();
	partial.forEach((n, i) => indexByCode.set(n.code, i));
	const effectiveParent = mode === 'compact' ? buildEffectiveCollapsedParentMap(pageIds, dca, nodeMap) : null;

	const result: PartialHierarchyRow[] = [];
	const emitted = new Set<string>();
	const displayDepthByCode = new Map<string, number>();

	function emit(code: string, parentCode: string | null): void {
		if (emitted.has(code) || !rowByCode.has(code)) {
			return;
		}
		let depth = 0;
		if (parentCode != null && displayDepthByCode.has(parentCode)) {
			depth = displayDepthByCode.get(parentCode)! + 1;
		}
		displayDepthByCode.set(code, depth);
		emitted.add(code);

		let insertIdx = result.length;
		if (parentCode != null) {
			const parentIdx = result.findIndex((r) => r.code === parentCode);
			if (parentIdx >= 0) {
				const parentDepth = displayDepthByCode.get(parentCode)!;
				insertIdx = parentIdx + 1;
				while (insertIdx < result.length && result[insertIdx].depth > parentDepth) {
					insertIdx++;
				}
			}
		}
		result.splice(insertIdx, 0, { ...rowByCode.get(code)!, depth });
	}

	function insertAfterEmittedParent(code: string): void {
		if (emitted.has(code) || !rowByCode.has(code)) {
			return;
		}
		const parent = nearestEmittedVisibleAncestor(code, emitted, visibleCodes, dca, nodeMap);
		emit(code, parent);
	}

	const pageIdsSorted = [...pageIds].sort(
		(a, b) => (orderInPage.get(a) ?? Number.MAX_SAFE_INTEGER) - (orderInPage.get(b) ?? Number.MAX_SAFE_INTEGER)
	);

	const rootChain = partialTrunkAncestorChainFromRoot(partial, dca);
	let rootParent: string | null = null;
	for (const code of rootChain) {
		if (visibleCodes.has(code)) {
			emit(code, rootParent);
			rootParent = code;
		}
	}
	if (dca != null && visibleCodes.has(dca)) {
		emit(dca, rootParent);
	}

	for (const pageId of pageIdsSorted) {
		if (mode === 'compact' && pageIds.has(pageId)) {
			const effParent = effectiveParent!.get(pageId) ?? dca;
			if (effParent != null && effParent !== dca) {
				const parentChain = visibleAncestorChainToPage(effParent, visibleCodes, dca, nodeMap);
				let parent: string | null = dca;
				for (const ancestor of parentChain) {
					emit(ancestor, parent);
					parent = ancestor;
				}
				emit(effParent, parent);
			}
			emit(pageId, effParent ?? dca);
		} else {
			const chain = visibleAncestorChainToPage(pageId, visibleCodes, dca, nodeMap);
			let parent: string | null = dca;
			for (const ancestor of chain) {
				emit(ancestor, parent);
				parent = ancestor;
			}
			emit(pageId, parent);
		}
	}

	for (const code of [...visibleCodes]
		.filter((c) => !emitted.has(c))
		.sort((a, b) => compareByPageOrderThenLayer(a, b, pageIds, orderInPage, nodeMap, indexByCode))) {
		insertAfterEmittedParent(code);
	}

	return result;
}

/**
 * Compact display: delegates to page-guided ordering with compact depth rules.
 */
export function buildCollapsedDisplayRows(
	rows: PartialHierarchyRow[],
	pageIds: Set<string>,
	dca: string | null,
	orderInPage: Map<string, number>,
	partial: PartialHierarchyNode[],
	nodeMap: Map<string, PartialHierarchyNode>
): PartialHierarchyRow[] {
	return buildPageGuidedDisplayRows(rows, pageIds, dca, orderInPage, partial, nodeMap, 'compact');
}

/** Depth-first compact order: group ancestors immediately above their page descendants. */
export function sortCollapsedHierarchyRows(
	rows: PartialHierarchyRow[],
	dca: string | null,
	orderInPage: Map<string, number>,
	partial: PartialHierarchyNode[] = [],
	pageIds: Set<string> = new Set(rows.filter((r) => r.onCurrentPage).map((r) => r.code)),
	nodeMap: Map<string, PartialHierarchyNode> = buildPartialHierarchyNodeMap(
		partial.filter((n) => rows.some((r) => r.code === n.code))
	)
): PartialHierarchyRow[] {
	return buildCollapsedDisplayRows(rows, pageIds, dca, orderInPage, partial, nodeMap);
}

/** Preserve Snowstorm layer-sorted order from the original {@code partial} array. */
export function sortFullHierarchyRows(
	rows: PartialHierarchyRow[],
	partial: PartialHierarchyNode[]
): PartialHierarchyRow[] {
	const indexByCode = new Map<string, number>();
	partial.forEach((n, i) => indexByCode.set(n.code, i));
	return [...rows].sort(
		(a, b) => (indexByCode.get(a.code) ?? Number.MAX_SAFE_INTEGER) - (indexByCode.get(b.code) ?? Number.MAX_SAFE_INTEGER)
	);
}

export function buildHierarchyDisplayRows(
	partial: PartialHierarchyNode[],
	pageIds: Set<string>,
	orderInPage: Map<string, number>,
	showFullPartialHierarchy: boolean
): PartialHierarchyRow[] {
	if (!partial.length || pageIds.size === 0) {
		return [];
	}

	const nodeMap = buildPartialHierarchyNodeMap(partial);
	const dca = findDeepestCommonAncestor(pageIds, nodeMap, partial);
	const nodes = showFullPartialHierarchy
		? nodesForFullView(partial, pageIds, dca)
		: nodesForCollapsedView(partial, pageIds, dca);
	const rows = buildPartialHierarchyRows(nodes, pageIds, nodeMap, dca, false);
	return buildPageGuidedDisplayRows(
		rows,
		pageIds,
		dca,
		orderInPage,
		partial,
		nodeMap,
		showFullPartialHierarchy ? 'full' : 'compact'
	);
}
