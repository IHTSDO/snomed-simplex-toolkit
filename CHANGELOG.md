# Changelog
All notable changes to this project will be documented in this file.

This project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## 3.0.1 Release (August 2026)
Major release replacing Weblate with Elasticsearch-backed Translation Studio (Snolate), plus translation workflow enhancements, release pipeline fixes, and dependency updates.

### Breaking
- Weblate is replaced with built-in Translation Studio
  - Migration strategy: Export each translation set from Weblate as CSV. Recreate sets in Translation Studio and import the CSVs.

### Features

- SIMPLEX-216 New Translation Studio to replace Weblate
- SIMPLEX-190 Upload translations via spreadsheet with multi-column synonym import
- SIMPLEX-220 Display hierarchy for translation units
- SIMPLEX-232 Populate translation set subsets using ECL
- SIMPLEX-233 Translation Studio Zen mode
- SIMPLEX-234 Pull translation sets into Snowstorm for Simplex users
- SIMPLEX-238 Track LLM API usage, concepts translated, and price in admin screen
- SIMPLEX-243 Edit translation set title and description in Translation Studio
- SIMPLEX-245 English and target term search filters on translation sets
- SIMPLEX-246 Import externally maintained concepts with existing SCTIDs via spreadsheet
- SIMPLEX-247 Translation set CSV download from Manage menu
- SIMPLEX-250 Opt-in US and GB English synonym artifacts with module-scoped translation counts
- SIMPLEX-251 Present AI batch translation as suggestions in Translation Studio
- SIMPLEX-253 Translation Studio CSV file import with mutable description acceptability
- SIMPLEX-255 Selectable rules in the language translation policy questionnaire
- SIMPLEX-270 Push dialog option to include ready-for-review units
- SIMPLEX-271 File upload concept selection for translation sets
- SIMPLEX-272 RTL support for Translation Studio target languages
- SIMPLEX-275 Visual ECL builder for manual subset selection

### Improvements

- SIMPLEX-198 Use true/false for the concept active column in custom concept spreadsheets
- SIMPLEX-225 Move ignore-case RVF assertion exclusions to configuration
- SIMPLEX-230 Translation Studio UX: status counts, filtering, intro copy, and growing concept tables
- SIMPLEX-235 Translation Studio performance caching with user refresh option
- SIMPLEX-239 Log details of Elasticsearch errors
- SIMPLEX-241 Configurable Snowstorm User-Agent header with version template
- SIMPLEX-242 Stream translation unit reads via Elasticsearch `search_after` paging
- SIMPLEX-248 Persist Translation Studio hierarchy sidebar toggles
- SIMPLEX-249 Filter Translation Sets by Language/Dialect
- SIMPLEX-252 Order-based context examples for batch AI translation
- SIMPLEX-257 Use editable language dialect name in AI translation policy
- SIMPLEX-259 Refresh translation sets after CodeSystem upgrade
- SIMPLEX-261 Auto-detect CSV delimiter on user file import
- SIMPLEX-262 Improve New Translation Set create form UX
- SIMPLEX-263 Translation Studio CSV import enhancements and import jobs panel
- SIMPLEX-264 Unify side-nav page layout with Edition Info pattern
- SIMPLEX-265 Split TranslationController and Translation Studio API
- SIMPLEX-266 Graceful degradation when RVF validation service is unavailable
- SIMPLEX-273 Remove language-wide spreadsheet import from Translation Studio
- SIMPLEX-274 Refine AI language translation policy questionnaire labels
- SIMPLEX-276 Persist Translation Studio Diagram toggle preference
- SIMPLEX-277 Apply shared term normalization in Translation Studio
- SIMPLEX-278 Update Simplex User Forum link URL
- Switch to snomed-parent-bom 3.14.1 and address dependency CVEs
- Centralize dependency version management in the parent POM
- Add security policy

### Fixes

- SIMPLEX-135 Fix classification out of sync blocking project approval
- SIMPLEX-213 Prevent concurrent release candidate and publish jobs
- SIMPLEX-227 Stop release candidate monitoring when SRS build fails
- SIMPLEX-231 Fix handling of validation system errors
- SIMPLEX-234 Fix duplicate translation export
- SIMPLEX-242 Fix translation unit stream sorting and Elasticsearch result window fallback
- SIMPLEX-254 Fix Translation CSV upload form not submitting on second use
- SIMPLEX-256 Fix Swagger UI returning 404
- SIMPLEX-258 Fix SNOMED upgrade loading all translation sources in one Elasticsearch query
- SIMPLEX-260 Fix Snolate translation set size to match unit count
- SIMPLEX-267 Stop Snowstorm pagination on partial pages and add configurable `searchAfter` guard
- SIMPLEX-268 Fix duplicate TranslationUnit documents and index repair
- SIMPLEX-269 Fix translation unit hierarchy order not synced from source
- Fix batch AI translate for large translation sets

## 2.12.1 Release (July 2026)
Maintenance release with bug fixes.

### Fixes

- SIMPLEX-236 Use Snowstorm to clear MDRS dates
- SIMPLEX-240 Make derivatives browser URL configurable in the frontend
- SIMPLEX-244 Use admin Weblate client for SNOMED upgrade
- Address dependency CVEs
