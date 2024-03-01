package org.snomed.simplex.service.validation;

import java.util.List;

public record ValidationFixList(int errorCount, int warningCount, List<ValidationFix> fixes) {

}
