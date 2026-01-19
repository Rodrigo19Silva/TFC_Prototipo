package pt.ulusofona.tfc;

import javax.swing.*;

public class Main {

    //variaveis globais
    private static String model = "gpt-4-turbo";
    static String apiKey = ""; // TODO: alterar | nÃ£o enviar para o GitHub

    static String server = ""; // TODO: alterar | nÃ£o enviar para o GitHub

    static boolean useHack = true;

    // ===== 6) main =====
    public static void main(String[] args) {

        SwingUtilities.invokeLater(() -> {
            GUI gui = new GUI(
                    new LLMInteractionEngine(server, apiKey, model, useHack)
            );
            gui.mostrarGUI();
        });

    }




}
