package org.snomed.simplex.util;

import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CsvParserTest {

	@Test
	void detectDelimiter_recognisesCommonSeparators() {
		assertThat(CsvParser.detectDelimiter("context,target")).isEqualTo(CsvParser.DELIMITER_COMMA);
		assertThat(CsvParser.detectDelimiter("context\ttarget")).isEqualTo(CsvParser.DELIMITER_TAB);
		assertThat(CsvParser.detectDelimiter("context;target")).isEqualTo(CsvParser.DELIMITER_SEMICOLON);
		assertThat(CsvParser.detectDelimiter("context|target")).isEqualTo(CsvParser.DELIMITER_PIPE);
	}

	@Test
	void detectDelimiter_handlesQuotedFieldsContainingOtherDelimiters() {
		assertThat(CsvParser.detectDelimiter("\"source,target\",context,developer_comments"))
				.isEqualTo(CsvParser.DELIMITER_COMMA);
		assertThat(CsvParser.detectDelimiter("source;\"target;notes\";context;developer_comments"))
				.isEqualTo(CsvParser.DELIMITER_SEMICOLON);
	}

	@Test
	void detectDelimiter_stripsBom() {
		assertThat(CsvParser.detectDelimiter("\uFEFFcontext\ttarget")).isEqualTo(CsvParser.DELIMITER_TAB);
	}

	@Test
	void detectDelimiter_fallsBackToCommaForSingleColumnHeader() {
		assertThat(CsvParser.detectDelimiter("onlyone")).isEqualTo(CsvParser.DELIMITER_COMMA);
	}

	@Test
	void detectDelimiter_prefersDelimiterWithMoreFieldsOrOccurrences() {
		assertThat(CsvParser.detectDelimiter("a,b|c")).isEqualTo(CsvParser.DELIMITER_COMMA);
		assertThat(CsvParser.detectDelimiter("a|b|c")).isEqualTo(CsvParser.DELIMITER_PIPE);
	}

	@Test
	void parseLine_handlesQuotedCommasAndEscapes() {
		assertThat(CsvParser.parseLine("a,b,c", CsvParser.DELIMITER_COMMA)).containsExactly("a", "b", "c");
		assertThat(CsvParser.parseLine("\"a,b\",c", CsvParser.DELIMITER_COMMA)).containsExactly("a,b", "c");
		assertThat(CsvParser.parseLine("\"say \"\"hi\"\"\",plain", CsvParser.DELIMITER_COMMA))
				.containsExactly("say \"hi\"", "plain");
	}

	@Test
	void parseLine_handlesTabAndSemicolonDelimiters() {
		assertThat(CsvParser.parseLine("context\ttarget", CsvParser.DELIMITER_TAB))
				.containsExactly("context", "target");
		assertThat(CsvParser.parseLine("context;target", CsvParser.DELIMITER_SEMICOLON))
				.containsExactly("context", "target");
	}

	@Test
	void readRow_handlesNewlinesInsideQuotedField() throws Exception {
		String csv = "Concept Code,Other Spanish Terms\n100,\"line1\nline2\"\n";
		StringReader reader = new StringReader(csv);
		assertThat(CsvParser.readRow(reader, CsvParser.DELIMITER_COMMA))
				.containsExactly("Concept Code", "Other Spanish Terms");
		assertThat(CsvParser.readRow(reader, CsvParser.DELIMITER_COMMA))
				.containsExactly("100", "line1\nline2");
		assertThat(CsvParser.readRow(reader, CsvParser.DELIMITER_COMMA)).isEmpty();
	}

	@Test
	void readRow_handlesTabDelimitedRows() throws Exception {
		String csv = "context\ttarget\n100\tasma\n";
		StringReader reader = new StringReader(csv);
		List<String> header = CsvParser.readRow(reader, CsvParser.DELIMITER_TAB);
		assertThat(header).containsExactly("context", "target");
		assertThat(CsvParser.readRow(reader, CsvParser.DELIMITER_TAB)).containsExactly("100", "asma");
	}
}
