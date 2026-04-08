/** US English language reference set — matches backend Concepts.US_LANG_REFSET */
const US_LANG_REFSET = '900000000000509007';
const IS_A_TYPE = '116680003';

export interface TranslationConceptContextRow {
	conceptId: string;
	conceptUri: string | null;
	descriptions: { role: string; term: string }[];
	parents: { term: string; conceptId: string }[];
	attributes: {
		typeLabel: string;
		valueKind: string;
		conceptTerm?: string | null;
		conceptId?: string | null;
		literalValue?: string | null;
	}[];
}

function acceptabilityPreferred(acceptabilityMap: Record<string, string> | null | undefined): boolean {
	return acceptabilityMap?.[US_LANG_REFSET] === 'PREFERRED';
}

function acceptabilityAcceptable(acceptabilityMap: Record<string, string> | null | undefined): boolean {
	return acceptabilityMap?.[US_LANG_REFSET] === 'ACCEPTABLE';
}

function isActive(component: { active?: boolean }): boolean {
	return component.active !== false;
}

function targetConceptId(target: any): string {
	return String(target?.conceptId ?? target?.id ?? '');
}

function targetFsnTerm(target: any): string {
	return target?.fsn?.term ?? '';
}

/**
 * Maps Snowstorm browser-format concept JSON to the structured rows used on the translation edit page.
 */
export function browserSnowstormConceptToContext(raw: any): TranslationConceptContextRow | null {
	if (!raw?.conceptId) {
		return null;
	}
	const conceptId = String(raw.conceptId);
	const parents: { term: string; conceptId: string }[] = [];
	const attributes: TranslationConceptContextRow['attributes'] = [];

	const rels = Array.isArray(raw.relationships) ? raw.relationships : [];
	for (const rel of rels) {
		if (!isActive(rel)) {
			continue;
		}
		const typeId = String(rel.typeId ?? '');
		const isIsA = typeId === IS_A_TYPE;
		const typeLabel = isIsA ? 'Parent' : (rel.type?.pt?.term ?? rel.type?.fsn?.term ?? 'Attribute');
		const concrete = rel.concreteValue;
		if (concrete != null && concrete.value != null) {
			attributes.push({
				typeLabel,
				valueKind: 'CONCRETE',
				literalValue: String(concrete.value),
				conceptTerm: null,
				conceptId: null
			});
			continue;
		}
		const target = rel.target;
		if (!target) {
			continue;
		}
		const tid = targetConceptId(target);
		const term = targetFsnTerm(target);
		if (isIsA) {
			parents.push({ term: term || tid, conceptId: tid });
		} else {
			attributes.push({
				typeLabel,
				valueKind: 'CONCEPT',
				conceptTerm: term || tid,
				conceptId: tid,
				literalValue: null
			});
		}
	}

	const descriptions: { role: string; term: string }[] = [];
	const descs = Array.isArray(raw.descriptions) ? raw.descriptions.filter(isActive) : [];
	const fsn = descs.find((d) => d.type === 'FSN' && acceptabilityPreferred(d.acceptabilityMap));
	if (fsn?.term) {
		descriptions.push({ role: 'FSN', term: fsn.term });
	}
	const pt = descs.find((d) => d.type === 'SYNONYM' && acceptabilityPreferred(d.acceptabilityMap));
	if (pt?.term) {
		descriptions.push({ role: 'PT', term: pt.term });
	}
	for (const d of descs) {
		if (d.type === 'SYNONYM' && acceptabilityAcceptable(d.acceptabilityMap) && d.term) {
			descriptions.push({ role: 'SYNONYM', term: d.term });
		}
	}

	return {
		conceptId,
		conceptUri: `https://snomed.info/id/${conceptId}`,
		descriptions,
		parents,
		attributes
	};
}
