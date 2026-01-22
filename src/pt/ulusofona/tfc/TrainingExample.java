package pt.ulusofona.tfc;

public class TrainingExample {
    private final String id;
    private final String instructions;
    private final String referenceCode;
    private final String testsCode;

    public TrainingExample(String id, String instructions, String referenceCode, String testsCode) {
        this.id = id;
        this.instructions = instructions;
        this.referenceCode = referenceCode;
        this.testsCode = testsCode;
    }

    public String getId() {
        return id;
    }

    public String getInstructions() {
        return instructions;
    }

    public String getReferenceCode() {
        return referenceCode;
    }

    public String getTestsCode() {
        return testsCode;
    }

}
