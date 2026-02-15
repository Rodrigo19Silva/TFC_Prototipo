package pt.ulusofona.tfc;

import pt.ulusofona.tfc.filters.NumericFilter;

import javax.swing.*;
import javax.swing.text.AbstractDocument;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;



public class GUI {
    private static JTextField pastaField;
    private static JTextArea infoTextArea;
    private static JComboBox<String> modeloComboBox;
    private static JComboBox<Integer> versoesComboBox;
    private static String ultimoConteudoGerado = null;



    private static LLMInteractionEngine engine;
    public GUI (LLMInteractionEngine engine) {
        // assign to static field explicitly
        GUI.engine = engine;
    }


    public static ArrayList<TrainingExample> leExemplos(File pasta) {
        ArrayList<TrainingExample> exemplos = new ArrayList<>();

        // verificar se a pasta existe ou é diretório
        if (pasta == null || !pasta.exists() || !pasta.isDirectory()) {
            System.out.println("A pasta " + pasta + " não existe");
            return exemplos;
        }

        //guardar nomes dos ficheiros
        String instructions = null;
        StringBuilder reference = new StringBuilder();
        StringBuilder tests = new StringBuilder();

        String[] objetos = pasta.list();
        int i = 0;

        while (objetos != null && i < objetos.length) {
            File ficheiro = new File(pasta, objetos[i]);

            if (ficheiro.isFile()) {
                String nome = ficheiro.getName();

                if (nome.equalsIgnoreCase("instructions.md") || nome.equalsIgnoreCase("instructions.html")) {
                    try (BufferedReader br = new BufferedReader(
                            new java.io.InputStreamReader(
                                    new java.io.FileInputStream(ficheiro),
                                    java.nio.charset.StandardCharsets.UTF_8))) {
                        StringBuilder conteudoDoFicheiro = new StringBuilder();
                        String linha;
                        while ((linha = br.readLine()) != null) {
                            conteudoDoFicheiro.append(linha).append("\n");
                        }
                        instructions = conteudoDoFicheiro.toString();
                    }
                    catch (IOException e) {
                                        throw new RuntimeException("Erro ao ler o ficheiro: " + ficheiro.getAbsolutePath(), e);
                                   }

                                }

                else if (nome.toLowerCase().endsWith(".java")) {

                    try (BufferedReader br = new BufferedReader(
                            new java.io.InputStreamReader(
                                    new java.io.FileInputStream(ficheiro),
                                    java.nio.charset.StandardCharsets.UTF_8
                            ))) {

                        StringBuilder conteudoDoFicheiro = new StringBuilder();
                        String linha;

                        while ((linha = br.readLine()) != null) {
                            conteudoDoFicheiro.append(linha).append("\n");
                        }

                        String conteudo = conteudoDoFicheiro.toString();

                        // decidir se é teste ou reference (pela regra "Test...")
                        if (nome.toLowerCase().startsWith("test")) {
                            tests.append("\n// ===== ").append(nome).append(" =====\n");
                            tests.append(conteudo);
                        } else {
                            reference.append("\n// ===== ").append(nome).append(" =====\n");
                            reference.append(conteudo);
                        }

                    } catch (IOException e) {
                        throw new RuntimeException("Erro ao ler o ficheiro: " + ficheiro.getAbsolutePath(), e);
                    }
                }

            }

            i++;
        }

        // só cria exemplo se tiver os 3 conteúdos
        String id = pasta.getName();
        if (instructions != null) {
            String referenceStr = (reference.length() > 0) ? reference.toString() : "";
            String testsStr = (tests.length() > 0) ? tests.toString() : "";

            exemplos.add(new TrainingExample(id, instructions, referenceStr, testsStr));
        }

        return exemplos;
    }

    private static ArrayList<TrainingExample> getInputFilesContents(String folder) {
        File pasta = new File(folder);
        if (!pasta.exists() || !pasta.isDirectory()) {
            JOptionPane.showMessageDialog(null, "Pasta inválida: " + pasta.getAbsolutePath(),
                    "Erro", JOptionPane.WARNING_MESSAGE);
            return null;
        }

        ArrayList<TrainingExample> exemplos = leExemplos(pasta);

        if (exemplos.isEmpty()) {
            JOptionPane.showMessageDialog(null,
                    "A pasta não contém um exemplo válido.\n" +
                            "Esperado: instructions.md, Main.java, TestMain.java",
                    "Sem exemplos", JOptionPane.WARNING_MESSAGE);
            return null;
        }
        return exemplos;
    }

    private static String criarPrompt(ArrayList<TrainingExample> partes) {
        StringBuilder resultado = new StringBuilder();

        // validação básica
        if (partes != null) {
            int i = 0;

            while (i < partes.size()) {

                // cada item é um TrainingExample (com instructions/reference/tests)
                TrainingExample exemplo = partes.get(i);

                // construir o JSON com os 3 conteúdos (instructions, reference, tests)
                String jsonTreino = "";
                jsonTreino += "{";
                jsonTreino += "\"instructions\":\"" + JSONUtils.escapeJsonString(exemplo.getInstructions()) + "\",";
                jsonTreino += "\"reference\":\"" + JSONUtils.escapeJsonString(exemplo.getReferenceCode()) + "\",";
                jsonTreino += "\"tests\":\"" + JSONUtils.escapeJsonString(exemplo.getTestsCode()) + "\"";
                jsonTreino += "}";

                // construir a prompt final
                resultado.append("Gera UMA NOVA versão do exercício com o MESMO nível de dificuldade e o MESMO tipo de requisitos.\n");
                resultado.append("Mas com um tema diferente do exemplo.\n");
                resultado.append("Não uses o mesmo domínio/nomes do exemplo.\n");
                resultado.append("Responde APENAS em JSON.\n");
                resultado.append("{ \"versions\": [ { \"id\": \"v1\", \"instructions\": \"...\", \"reference\": \"...\", \"tests\": \"...\" } ] }\n\n");
                resultado.append("EXEMPLO DE TREINO:\n");
                resultado.append(jsonTreino);

                if (i < partes.size() - 1) {
                    resultado.append("\n\n");
                }

                i++;
            }
        }

        return resultado.toString();
    }

    public static void processarPedido(String folder, String modelo, int nrVersoes) {

        ArrayList<TrainingExample> exemplos = getInputFilesContents(folder);
        if (exemplos == null) return;

        engine.model = modelo;

        infoTextArea.append("Ficheiros lidos: " + exemplos.size() + "\n");

        String prompt = criarPrompt(exemplos);

        System.out.println("Prompt length = " + prompt.length());

        for (int versao = 1; versao <= nrVersoes; versao++) {
            infoTextArea.append("Versão " + versao + "...\n");
            try {
                String jsonResposta = engine.sendPrompt(prompt);
                String resposta = JSONUtils.getJsonString(jsonResposta, "text");

                System.out.println(jsonResposta);

                infoTextArea.append("\nResposta " + versao + " ---\n");

                if (resposta != null) {
                    infoTextArea.append(resposta);
                    ultimoConteudoGerado = resposta;
                } else {
                    infoTextArea.append(jsonResposta);
                    ultimoConteudoGerado = jsonResposta;
                }


                infoTextArea.append("\n\n");

            } catch (Exception erro) {
                JOptionPane.showMessageDialog(null, "Erro: " + erro.getMessage(),
                        "Erro", JOptionPane.ERROR_MESSAGE);
                return;
            }
        }

        infoTextArea.append("Concluído.\n");
    }

    //guardar o ficheiro gerado pelo llm
    private static void guardarEmFicheiro(String nomeFicheiro, String conteudo) throws IOException {
        Path pastaSaida = Path.of("generated");
        Files.createDirectories(pastaSaida);

        Path ficheiroSaida = pastaSaida.resolve(nomeFicheiro);
        Files.writeString(ficheiroSaida, conteudo, StandardCharsets.UTF_8);

        System.out.println("Guardado em: " + ficheiroSaida.toAbsolutePath());
    }



    // grid layout
    public static void mostrarGUI() {
        JFrame window = new JFrame("TFC do Rodrigo");
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        window.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

        JPanel painel = new JPanel(new GridBagLayout());
        painel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // pasta a selecionar
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.0; gbc.fill = GridBagConstraints.NONE;
        painel.add(new JLabel("Pasta:"), gbc);

        pastaField = new JTextField();
        pastaField.setEditable(false);
        gbc.gridx = 1; gbc.weightx = 1.0; gbc.fill = GridBagConstraints.HORIZONTAL;
        painel.add(pastaField, gbc);

        JButton selecionarPastaButton = new JButton("Selecionar Pasta");
        selecionarPastaButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                JFileChooser seletor = new JFileChooser();
                seletor.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                int resultado = seletor.showOpenDialog(window);
                if (resultado == JFileChooser.APPROVE_OPTION) {
                    File pasta = seletor.getSelectedFile();
                    pastaField.setText(pasta.getAbsolutePath());
                    infoTextArea.append("Pasta selecionada: " + pasta.getAbsolutePath() + "\n");
                }
            }
        });
        gbc.gridx = 2; gbc.weightx = 0.0; gbc.fill = GridBagConstraints.NONE;
        painel.add(selecionarPastaButton, gbc);

        // modelo llm
        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0.0; gbc.fill = GridBagConstraints.NONE;
        painel.add(new JLabel("Modelo:"), gbc);

        modeloComboBox = new JComboBox<>(obterModelos());
        gbc.gridx = 1; gbc.gridwidth = 2; gbc.weightx = 1.0; gbc.fill = GridBagConstraints.HORIZONTAL;
        painel.add(modeloComboBox, gbc);
        gbc.gridwidth = 1;

        // nr versões
        gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 0.0; gbc.fill = GridBagConstraints.NONE;
        painel.add(new JLabel("Versões:"), gbc);

        Integer[] options = {1,2,3,4,5,6,7,8,9,10};
        versoesComboBox = new JComboBox<>(options);
        versoesComboBox.setEditable(true);
        JTextField editor = (JTextField) versoesComboBox.getEditor().getEditorComponent();
        ((AbstractDocument) editor.getDocument()).setDocumentFilter(new NumericFilter());
        editor.setText("1"); // default

        gbc.gridx = 1; gbc.weightx = 0.3; gbc.fill = GridBagConstraints.HORIZONTAL;
        painel.add(versoesComboBox, gbc);

        gbc.gridx = 2; gbc.weightx = 0.7;
        painel.add(Box.createHorizontalStrut(1), gbc);

        // textarea onde aparece as informações
        gbc.gridx = 0; gbc.gridy = 3; gbc.anchor = GridBagConstraints.NORTHWEST;
        painel.add(new JLabel("Informações:"), gbc);

        infoTextArea = new JTextArea(12, 60);
        infoTextArea.setEditable(false);
        infoTextArea.setLineWrap(true);
        infoTextArea.setWrapStyleWord(true);
        JScrollPane scrollPane = new JScrollPane(infoTextArea);

        gbc.gridx = 1; gbc.gridy = 3; gbc.gridwidth = 2;
        gbc.weightx = 1.0; gbc.weighty = 1.0; gbc.fill = GridBagConstraints.BOTH;
        painel.add(scrollPane, gbc);

        gbc.gridwidth = 1; gbc.weighty = 0.0; gbc.fill = GridBagConstraints.HORIZONTAL;

        //guardar ficheiro
        JButton guardarButton = new JButton("Guardar");
        guardarButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    if (ultimoConteudoGerado == null || ultimoConteudoGerado.isBlank()) {
                        JOptionPane.showMessageDialog(window,
                                "Ainda não existe resultado para guardar. Clica primeiro em Submeter.",
                                "Nada para guardar", JOptionPane.WARNING_MESSAGE);
                        return;
                    }


                    String conteudo = ultimoConteudoGerado;
                    String ext = conteudo.trim().startsWith("<") ? ".html" : ".md";

                    String nome = "instructions_" + System.currentTimeMillis() + ext;

                    guardarEmFicheiro(nome, conteudo);

                    JOptionPane.showMessageDialog(window,
                            "Guardado em generated/" + nome,
                            "OK", JOptionPane.INFORMATION_MESSAGE);

                } catch (Exception erro) {
                    JOptionPane.showMessageDialog(window,
                            "Erro ao guardar: " + erro.getMessage(),
                            "Erro", JOptionPane.ERROR_MESSAGE);
                }
            }
        });



        // submeter
        JButton enviarButton = new JButton("Submeter");
        enviarButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String pasta = pastaField.getText().trim();
                if (pasta.isEmpty()) {
                    JOptionPane.showMessageDialog(window, "Seleciona uma pasta primeiro.",
                            "Pasta em falta", JOptionPane.WARNING_MESSAGE);
                    return;
                }

                String modelo = (String) modeloComboBox.getSelectedItem();
                if (modelo == null || modelo.isBlank()) {
                    JOptionPane.showMessageDialog(window, "Seleciona um modelo.",
                            "Modelo em falta", JOptionPane.WARNING_MESSAGE);
                    return;
                }

                Object sel = versoesComboBox.getSelectedItem();
                int nrVersoes;

                if (sel instanceof Integer) {
                    nrVersoes = (Integer) sel;
                } else {
                    if (sel == null) {
                        JOptionPane.showMessageDialog(window, "Indique o numero de versões",
                                "Valor em falta", JOptionPane.WARNING_MESSAGE);
                        return;
                    }
                    String txt = sel.toString().trim();
                    if (txt.isEmpty()) {
                        JOptionPane.showMessageDialog(window, "Indique o numero de versões",
                                "Valor em falta", JOptionPane.WARNING_MESSAGE);
                        return;
                    }
                    nrVersoes = Integer.parseInt(txt);
                }

                if (nrVersoes < 1) {
                    JOptionPane.showMessageDialog(window, "O numero de versões tem de ser maior ou igual que 1.",
                            "Valor inválido", JOptionPane.WARNING_MESSAGE);
                    return;
                }

                infoTextArea.append("A enviar dados...\n");
                infoTextArea.append("Pasta: " + pasta + "\n");
                infoTextArea.append("Modelo: " + modelo + "\n");

                processarPedido(pasta, modelo, nrVersoes);
            }
        });

        gbc.gridx = 1; gbc.gridy = 4; gbc.anchor = GridBagConstraints.SOUTHEAST; gbc.fill = GridBagConstraints.NONE;
        painel.add(guardarButton, gbc);

        gbc.gridx = 2; gbc.gridy = 4; gbc.anchor = GridBagConstraints.SOUTHEAST; gbc.fill = GridBagConstraints.NONE;
        painel.add(enviarButton, gbc);

        window.setContentPane(painel);
        window.pack();
        window.setMinimumSize(new Dimension(800, 500));
        window.setLocationRelativeTo(null);
        window.setVisible(true);
    }

    static String[] obterModelos() {
        return new String[]{"gpt-4o-mini", "gpt-4o", "gpt-4.1-mini", "gpt-4-turbo"};
    }

}