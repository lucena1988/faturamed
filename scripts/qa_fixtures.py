"""Planilhas ficticias autocontidas para a homologacao HTTP."""
import io
from openpyxl import Workbook


def workbook_bytes(rows):
    workbook = Workbook()
    sheet = workbook.active
    sheet.title = 'Planilha2'
    for row_index, values in enumerate(rows, 1):
        for column, value in enumerate(values, 1):
            cell = sheet.cell(row_index, column, str(value))
            # Preserve the old fixture semantics: every value is literal text.
            cell.data_type = 's'
    output = io.BytesIO()
    workbook.save(output)
    workbook.close()
    return output.getvalue()
