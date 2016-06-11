package org.cobbzilla.util.handlebars;

import com.github.jknack.handlebars.Handlebars;
import lombok.Cleanup;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDCheckBox;
import org.apache.pdfbox.pdmodel.interactive.form.PDField;
import org.cobbzilla.util.io.FileUtil;

import java.io.File;
import java.io.InputStream;
import java.util.Map;

import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.util.io.FileUtil.abs;
import static org.cobbzilla.util.io.FileUtil.temp;

@Slf4j
public class PdfMerger {

    public static File[] merge(InputStream in,
                               Map<String, Object> context,
                               Handlebars handlebars) throws Exception {

        final Map<String, String> fieldMappings = (Map<String, String>) context.get("fields");

        // load the document
        final PDDocument pdfDocument = PDDocument.load(in);

        // get the document catalog
        final PDAcroForm acroForm = pdfDocument.getDocumentCatalog().getAcroForm();

        // as there might not be an AcroForm entry a null check is necessary
        if (acroForm != null) {
            // Retrieve an individual field and set its value.
            for (PDField field : acroForm.getFields()) {
                try {
                    String fieldValue = fieldMappings.get(field.getFullyQualifiedName());
                    if (!empty(fieldValue)) {
                        fieldValue = HandlebarsUtil.apply(handlebars, fieldValue, context);
                    }
                    if (field instanceof PDCheckBox) {
                        PDCheckBox box = (PDCheckBox) field;
                        if (!empty(fieldValue) && Boolean.valueOf(fieldValue)) {
                            box.check();
                        } else {
                            box.unCheck();
                        }

                    } else {
                        String formValue = field.getValueAsString();
                        if (empty(formValue)) formValue = fieldValue;
                        if (!empty(formValue)) {
                            formValue = HandlebarsUtil.apply(handlebars, formValue, context);
                            field.setValue(formValue);
                        }
                    }
                } catch (Exception e) {
                    die("merge: "+e, e);
                }
            }
        }

        // add images
        if (fieldMappings != null) {
            for (Map.Entry<String, String> fieldEntries : fieldMappings.entrySet()) {
                if (fieldEntries.getKey().startsWith("@")) {
                    final ImageInsertion insertion = new ImageInsertion(fieldEntries.getValue());

                    // write image to temp file
                    @Cleanup("delete") final File imageTemp = temp("."+insertion.getFormat());
                    FileUtil.toFile(imageTemp, insertion.getImageStream());

                    // open stream for writing inserted image
                    final PDPage page = pdfDocument.getDocumentCatalog().getPages().get(insertion.getPage());
                    final PDPageContentStream contentStream = new PDPageContentStream(pdfDocument, page, PDPageContentStream.AppendMode.APPEND, true);

                    // draw image on page
                    final PDImageXObject image = PDImageXObject.createFromFile(abs(imageTemp), pdfDocument);
                    contentStream.drawImage(image, insertion.getX(), insertion.getY(), insertion.getWidth(), insertion.getHeight());
                    contentStream.close();
                }
            }
        }

        final File output = temp(".pdf");

        // Save and close the filled out form.
        pdfDocument.save(output);
        pdfDocument.close();

        return new File[] { output };
    }
}
