package br.com.faturamed.conciliacao;

import java.util.*;
import java.util.regex.Pattern;

/** Regras dos dados que o proprio medico completa no cadastro. */
public final class ValidacaoCadastroMedico {
    private static final Pattern EMAIL=Pattern.compile("^[^@\\s;]+@[^@\\s;]+\\.[^@\\s;]+$");
    private ValidacaoCadastroMedico() {}

    /** Nome com pelo menos dois nomes (ex.: "Luiz" sozinho nao serve). */
    public static String nomeCompleto(String nome) {
        String limpo=nome==null?"":nome.trim().replaceAll("\\s+"," ");
        if(limpo.length()>180) throw new IllegalArgumentException("Nome com no maximo 180 caracteres");
        long partes=Arrays.stream(limpo.split(" ")).filter(p->p.replaceAll("[^\\p{L}]","").length()>=2).count();
        if(partes<2) throw new IllegalArgumentException("Informe o nome completo, com pelo menos nome e sobrenome");
        return limpo;
    }

    /** Um ou mais e-mails separados por ";" (ou virgula); devolve normalizado, sem repeticao. */
    public static List<String> emails(String valor) {
        List<String> lista=new ArrayList<>();
        for(String parte:Objects.toString(valor,"").split("[;,]")) {
            String email=parte.trim().toLowerCase(Locale.ROOT);
            if(email.isEmpty()) continue;
            if(!EMAIL.matcher(email).matches()) throw new IllegalArgumentException("E-mail invalido: "+email);
            if(!lista.contains(email)) lista.add(email);
        }
        if(lista.isEmpty()) throw new IllegalArgumentException("Informe pelo menos um e-mail");
        if(String.join(";",lista).length()>500) throw new IllegalArgumentException("Lista de e-mails muito longa");
        return List.copyOf(lista);
    }

    public static String crm(String valor) {
        String crm=Objects.toString(valor,"").replaceAll("\\s","");
        if(!crm.matches("[0-9]{1,20}")) throw new IllegalArgumentException("Informe o CRM com numeros");
        return crm.replaceFirst("^0+(?!$)","");
    }

    public static String cpf(String valor) {
        String cpf=Objects.toString(valor,"").replaceAll("\\D","");
        if(cpf.length()!=11 || cpf.chars().distinct().count()==1 || !digitosConferem(cpf,9,10) || !digitosConferem(cpf,10,11))
            throw new IllegalArgumentException("CPF invalido");
        return cpf;
    }

    public static String cnpj(String valor) {
        String cnpj=Objects.toString(valor,"").replaceAll("\\D","");
        if(cnpj.length()!=14 || cnpj.chars().distinct().count()==1 || !digitoCnpj(cnpj,12) || !digitoCnpj(cnpj,13))
            throw new IllegalArgumentException("CNPJ invalido");
        return cnpj;
    }

    // CPF: pesos decrescentes a partir de (posicao+1).
    private static boolean digitosConferem(String cpf,int posicao,int peso) {
        int soma=0;
        for(int i=0;i<posicao;i++) soma+=(cpf.charAt(i)-'0')*(peso-i);
        int resto=(soma*10)%11;
        return (resto==10?0:resto)==cpf.charAt(posicao)-'0';
    }

    // CNPJ: pesos 5..2,9..2 (12 digitos) e 6..2,9..2 (13 digitos).
    private static boolean digitoCnpj(String cnpj,int posicao) {
        int soma=0,peso=posicao-7;
        for(int i=0;i<posicao;i++) { soma+=(cnpj.charAt(i)-'0')*peso; peso=peso==2?9:peso-1; }
        int resto=soma%11;
        return (resto<2?0:11-resto)==cnpj.charAt(posicao)-'0';
    }
}
