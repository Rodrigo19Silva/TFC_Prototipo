package pt.ulusofona.tfc;

public enum EnumTipoFicheiro {
    INSTRUCTIONS("instructions"),
    REFERENCE("Main"),
    UNIT_TESTS("TestMain");

    private final String nomeFicheiro;

    EnumTipoFicheiro(String nomeFicheiro) {
        this.nomeFicheiro = nomeFicheiro;
    }

    public String getNomeFicheiro() {
        return nomeFicheiro;
    }
}
