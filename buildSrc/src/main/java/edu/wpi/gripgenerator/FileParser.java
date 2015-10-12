package edu.wpi.gripgenerator;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseException;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.expr.NameExpr;
import edu.wpi.gripgenerator.defaults.DefaultValueCollector;
import edu.wpi.gripgenerator.defaults.ObjectDefaultValue;
import edu.wpi.gripgenerator.settings.DefinedMethod;
import edu.wpi.gripgenerator.settings.DefinedMethodCollection;
import edu.wpi.gripgenerator.settings.DefinedParamType;
import edu.wpi.gripgenerator.templates.OperationList;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class FileParser {
    /**
     * Regex splits the parameter into three distinct capture groups.
     * <ol>
     * <li>The type and the param with optional varargs.</li>
     * <li>The comment that is after the parameter.</li>
     * <li>The various ways that the parameter can end.</li>
     * </ol>
     */
    protected static final String methodReorderPattern = "([A-Za-z1-9]+ (?:\\.\\.\\.)?[a-z][A-Za-z0-9_]*)(/\\*=[^ ]*\\*/)((?:,)|(?:\\s*\\)))";

    /**
     * Reorders the {@link FileParser#methodReorderPattern} capture groups so the JavaParser can correctly
     * associate the params with their respective comments.
     */
    protected static final String methodNewOrder = "$2$1$3";

    /**
     * There is a bug in the JavaParser that will incorrectly associate comments after a parameter but
     * before a comma will incorrectly associate that comment with the next param in the method's params.
     *
     * @param stream The original input file.
     * @return The processed output stream.
     * @see <a href="https://github.com/javaparser/javaparser/issues/199">Javaparser Issue:199</a>
     */
    private static InputStream preProcessStream(InputStream stream) {
        //FIXME: This is a hack around. This should be removed once the above noted issue is resolved.
        java.util.Scanner s = new java.util.Scanner(stream).useDelimiter("\\A");
        String input = s.hasNext() ? s.next() : "";
        input = input.replaceAll(methodReorderPattern, methodNewOrder);
        return new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8));
    }

    private static CompilationUnit readFile(URL url) {
        try {
            return JavaParser.parse(preProcessStream(url.openStream()));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } catch (ParseException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Generates all of the source code from the opencv bindings
     *
     * @return A map of the filename with the compilation units
     */
    public static Map<String, CompilationUnit> generateAllSourceCode() {
        URL INPUT_URL = FileParser.class.getResource("/org/bytedeco/javacpp/opencv_core.txt");
        CompilationUnit compilationUnit = readFile(INPUT_URL);
        Map<String, CompilationUnit> returnMap = new HashMap<>();
        DefaultValueCollector collector = new DefaultValueCollector();

        OperationList operationList = new OperationList(
                new ImportDeclaration(new NameExpr("edu.wpi.grip.generated.opencv_core"), false, true),
                new ImportDeclaration(new NameExpr("edu.wpi.grip.generated.opencv_imgproc"), false, true)
        );

        if (compilationUnit != null) {
            returnMap.putAll(parseOpenCVCore(compilationUnit, collector, operationList));
        } else {
            System.err.print("Invalid File input");
        }
        URL INPUT_URL2 = FileParser.class.getResource("/org/bytedeco/javacpp/opencv_imgproc.txt");
        compilationUnit = readFile(INPUT_URL2);
        if (compilationUnit != null) {
            returnMap.putAll(parseOpenImgprc(compilationUnit, collector, operationList));

        }

        // Generate the Operation List class last
        returnMap.put(operationList.getClassName(), operationList.getDeclaration());
        return returnMap;
    }

    public static Map<String, CompilationUnit> parseOpenImgprc(CompilationUnit imgprocDeclaration, DefaultValueCollector collector, OperationList operations) {
        Map<String, CompilationUnit> compilationUnits = new HashMap<>();
        DefinedMethodCollection collection = new DefinedMethodCollection("opencv_imgproc",
                new DefinedMethod("Sobel", false, "Mat", "Mat"),
                new DefinedMethod("medianBlur", false, "Mat", "Mat"),
                new DefinedMethod("GaussianBlur", false,
                        new DefinedParamType("Mat"),
                        new DefinedParamType("Mat"),
                        new DefinedParamType("Size").setDefaultValue(new ObjectDefaultValue("Size", "1", "1"))
                ),
                new DefinedMethod("Laplacian", "Mat", "Mat"),
                new DefinedMethod("dilate", false, "Mat", "Mat"),
                new DefinedMethod("Canny", false, new DefinedParamType("Mat"), new DefinedParamType("Mat", DefinedParamType.DefinedParamState.OUTPUT)),
                new DefinedMethod("cornerMinEigenVal", false, "Mat", "Mat"),
                new DefinedMethod("cornerHarris", false, "Mat", "Mat"),
                new DefinedMethod("cornerEigenValsAndVecs", false, "Mat", "Mat")
        ).setOutputDefaults("dst");
        new OpenCVMethodVisitor(collection).visit(imgprocDeclaration, compilationUnits);
        collection.generateCompilationUnits(collector, compilationUnits, operations);
        return compilationUnits;
    }

    public static Map<String, CompilationUnit> parseOpenCVCore(CompilationUnit coreDeclaration, DefaultValueCollector collector, OperationList operations) {
        Map<String, CompilationUnit> compilationUnits = new HashMap<>();

        new OpenCVEnumVisitor(collector).visit(coreDeclaration, compilationUnits);

        DefinedMethodCollection collection = new DefinedMethodCollection("opencv_core",
                new DefinedMethod("add", false, "Mat", "Mat", "Mat"),
                new DefinedMethod("subtract", false, "Mat", "Mat", "Mat").addDescription("Calculates the per-pixel difference between two images"),
                new DefinedMethod("multiply", false, "Mat", "Mat", "Mat"),
                new DefinedMethod("divide", false, "Mat", "Mat", "Mat"),
                new DefinedMethod("scaleAdd", false, "Mat", "double", "Mat", "Mat"),
                new DefinedMethod("normalize", false, "Mat", "Mat"),
                new DefinedMethod("batchDistance", false, "Mat", "Mat"),
                new DefinedMethod("addWeighted", false, "Mat"),
                new DefinedMethod("flip", false, "Mat", "Mat"),
                new DefinedMethod("bitwise_and", false, "Mat", "Mat"),
                new DefinedMethod("bitwise_or", false, "Mat", "Mat"),
                new DefinedMethod("bitwise_xor", false, "Mat", "Mat"),
                new DefinedMethod("bitwise_not", false, "Mat", "Mat"),
                new DefinedMethod("absdiff", false, "Mat", "Mat"),
                // TODO: Fix (Causes Segfault)
                //new DefinedMethod("inRange", false),
                new DefinedMethod("compare", true,
                        new DefinedParamType("Mat"),
                        new DefinedParamType("Mat"),
                        new DefinedParamType("Mat"),
                        new DefinedParamType("int").setLiteralDefaultValue("CMP_EQ")
                ),
                new DefinedMethod("max", false, "Mat", "Mat"),
                new DefinedMethod("min", false, "Mat", "Mat")
//                new DefinedMethod("sqrt", false, "Mat", "Mat"),
//                new DefinedMethod("pow", false,
//                        new DefinedParamType("Mat"),
//                        new DefinedParamType("double")
//                                .setDefaultValue(new PrimitiveDefaultValue(new PrimitiveType(PrimitiveType.Primitive.Double), "1"))
//                )
        ).setOutputDefaults("dst");
        new OpenCVMethodVisitor(collection).visit(coreDeclaration, compilationUnits);

        collection.generateCompilationUnits(collector, compilationUnits, operations);


        return compilationUnits;
    }

    public static void main(String... args) {
        FileParser.generateAllSourceCode();
    }
}
