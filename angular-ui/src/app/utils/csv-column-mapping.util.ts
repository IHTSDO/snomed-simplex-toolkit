export interface CsvColumnMapping {
	conceptColumn: string;
	termColumns: string[];
}

export function parseCsvLine(line: string): string[] {
	const fields: string[] = [];
	if (!line) {
		return fields;
	}
	let field = '';
	let inQuotes = false;
	for (let i = 0; i < line.length; i++) {
		const c = line.charAt(i);
		if (inQuotes) {
			if (c === '"') {
				if (i + 1 < line.length && line.charAt(i + 1) === '"') {
					field += '"';
					i++;
				} else {
					inQuotes = false;
				}
			} else {
				field += c;
			}
		} else if (c === '"') {
			inQuotes = true;
		} else if (c === ',') {
			fields.push(field);
			field = '';
		} else {
			field += c;
		}
	}
	fields.push(field);
	return fields;
}

export function parseFirstCsvRow(text: string): string[] {
	const fields: string[] = [];
	let field = '';
	let inQuotes = false;
	for (let i = 0; i < text.length; i++) {
		const c = text.charAt(i);
		if (inQuotes) {
			if (c === '"') {
				if (i + 1 < text.length && text.charAt(i + 1) === '"') {
					field += '"';
					i++;
				} else {
					inQuotes = false;
				}
			} else {
				field += c;
			}
		} else if (c === '"') {
			inQuotes = true;
		} else if (c === ',') {
			fields.push(field);
			field = '';
		} else if (c === '\r') {
			if (i + 1 < text.length && text.charAt(i + 1) === '\n') {
				i++;
			}
			fields.push(field);
			return fields;
		} else if (c === '\n') {
			fields.push(field);
			return fields;
		} else {
			field += c;
		}
	}
	if (field.length > 0 || fields.length > 0) {
		fields.push(field);
	}
	return fields;
}

export async function readCsvHeaders(file: File): Promise<string[]> {
	const chunk = await file.slice(0, 65536).text();
	const headers = parseFirstCsvRow(chunk).map((header) => header.trim());
	return headers.filter((header) => header.length > 0);
}

export function detectCsvColumnMapping(headers: string[]): CsvColumnMapping {
	if (headers.includes('Concept Code')) {
		const preferred = headers.find((header) => header.endsWith(' Preferred Term'));
		const other = headers.find((header) => header.startsWith('Other ') && header.endsWith(' Terms'));
		const termColumns = [preferred, other].filter((header): header is string => !!header);
		return {
			conceptColumn: 'Concept Code',
			termColumns
		};
	}
	if (headers.includes('context')) {
		return {
			conceptColumn: 'context',
			termColumns: headers.includes('target') ? ['target'] : []
		};
	}
	if (headers.length >= 2) {
		return {
			conceptColumn: headers[0],
			termColumns: [headers[1]]
		};
	}
	return {
		conceptColumn: headers[0] ?? '',
		termColumns: []
	};
}
