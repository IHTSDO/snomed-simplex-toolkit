package org.snomed.simplex.snolate.sets;

import org.snomed.simplex.snolate.domain.TranslationStatus;
import org.snomed.simplex.snolate.domain.TranslationUnit;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/**
 * Merges duplicate {@link TranslationUnit} documents during one-off index repair only.
 */
public final class TranslationUnitMerger {

	private TranslationUnitMerger() {
	}

	public record MergeResult(TranslationUnit canonical, List<String> orphanDocumentIds, List<String> warnings) {
	}

	public static MergeResult merge(String compositeLanguageCode, String code, List<TranslationUnit> duplicates,
			List<String> warnings) {
		if (duplicates == null || duplicates.isEmpty()) {
			throw new IllegalArgumentException("No duplicates to merge for code " + code);
		}
		TranslationUnit merged = new TranslationUnit();
		merged.setCode(code);
		merged.setCompositeLanguageCode(compositeLanguageCode);
		merged.setId(TranslationUnit.canonicalDocumentId(compositeLanguageCode, code));

		LinkedHashSet<String> memberOf = new LinkedHashSet<>();
		LinkedHashSet<String> aiSuggestions = new LinkedHashSet<>();
		int minOrder = Integer.MAX_VALUE;
		String refsetId = null;
		String languageCode = null;

		List<TranslationUnit> withTerms = new ArrayList<>();
		for (TranslationUnit doc : duplicates) {
			if (doc.getMemberOf() != null) {
				memberOf.addAll(doc.getMemberOf());
			}
			if (doc.getAiSuggestions() != null) {
				aiSuggestions.addAll(doc.getAiSuggestions());
			}
			minOrder = Math.min(minOrder, doc.getOrder());
			refsetId = preferNonBlank(refsetId, doc.getRefsetId(), code, "refsetId", warnings);
			languageCode = preferNonBlank(languageCode, doc.getLanguageCode(), code, "languageCode", warnings);
			if (doc.hasTermContent()) {
				withTerms.add(doc);
			}
		}
		if (minOrder == Integer.MAX_VALUE) {
			minOrder = 0;
		}

		merged.setMemberOf(memberOf);
		merged.setAiSuggestions(new ArrayList<>(aiSuggestions));
		merged.setOrder(minOrder);
		merged.setRefsetId(refsetId != null ? refsetId : "");
		merged.setLanguageCode(languageCode != null ? languageCode : "");

		List<String> mergedTerms = pickTerms(withTerms, code, warnings);
		merged.setTerms(mergedTerms);
		merged.setStatus(pickStatus(mergedTerms, duplicates));

		List<String> orphanIds = new ArrayList<>();
		String canonicalId = merged.getId();
		for (TranslationUnit doc : duplicates) {
			String docId = doc.getId();
			if (docId != null && !canonicalId.equals(docId)) {
				orphanIds.add(docId);
			}
		}

		return new MergeResult(merged, orphanIds, warnings);
	}

	private static String preferNonBlank(String current, String candidate, String code, String field, List<String> warnings) {
		if (candidate == null || candidate.isBlank()) {
			return current;
		}
		if (current == null || current.isBlank()) {
			return candidate;
		}
		if (!current.equals(candidate)) {
			warnings.add("Concept %s: conflicting %s values '%s' and '%s'; keeping '%s'.".formatted(
					code, field, current, candidate, current));
		}
		return current;
	}

	private static List<String> pickTerms(List<TranslationUnit> withTerms, String code, List<String> warnings) {
		if (withTerms.isEmpty()) {
			return List.of();
		}
		if (withTerms.size() == 1) {
			return new ArrayList<>(withTerms.get(0).getTerms());
		}
		TranslationUnit best = withTerms.stream()
				.max((a, b) -> Integer.compare(statusProgressRank(a.getStatus()), statusProgressRank(b.getStatus())))
				.orElse(withTerms.get(0));
		List<String> chosen = new ArrayList<>(best.getTerms());
		for (TranslationUnit doc : withTerms) {
			if (doc == best) {
				continue;
			}
			if (!termsEqual(chosen, doc.getTerms())) {
				warnings.add("Concept %s: conflicting translation terms; kept terms from status %s.".formatted(
						code, best.getStatus()));
				break;
			}
		}
		return chosen;
	}

	private static boolean termsEqual(List<String> a, List<String> b) {
		if (a == null || a.isEmpty()) {
			return b == null || b.isEmpty();
		}
		return a.equals(b);
	}

	private static TranslationStatus pickStatus(List<String> mergedTerms, List<TranslationUnit> duplicates) {
		if (mergedTerms == null || mergedTerms.isEmpty()) {
			return TranslationStatus.NOT_STARTED;
		}
		return duplicates.stream()
				.map(TranslationUnit::getStatus)
				.filter(Objects::nonNull)
				.max((a, b) -> Integer.compare(statusProgressRank(a), statusProgressRank(b)))
				.orElse(TranslationStatus.NOT_STARTED);
	}

	static int statusProgressRank(TranslationStatus status) {
		if (status == null) {
			return 0;
		}
		return switch (status) {
			case COMPLETE -> 5;
			case APPROVED -> 4;
			case FOR_REVIEW -> 3;
			case NEEDS_EDIT -> 2;
			case NOT_STARTED -> 1;
		};
	}

	public static boolean needsRepair(String compositeLanguageCode, String code, List<TranslationUnit> group) {
		if (group.size() > 1) {
			return true;
		}
		if (group.isEmpty()) {
			return false;
		}
		String canonicalId = TranslationUnit.canonicalDocumentId(compositeLanguageCode, code);
		TranslationUnit only = group.get(0);
		return only.getId() == null || !canonicalId.equals(only.getId());
	}
}
