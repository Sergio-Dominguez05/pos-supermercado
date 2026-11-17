package gt.supermercado.pos.clientes;

import java.util.concurrent.atomic.AtomicLong;

/** NIT válidos y distintos para las pruebas: un cliente no se registra dos veces con el mismo NIT. */
public final class NitsDePrueba {

    private static final AtomicLong SIGUIENTE = new AtomicLong(System.nanoTime() % 1_000_000_000L);

    private NitsDePrueba() {
    }

    /** Un NIT nuevo con su dígito verificador correcto (módulo 11 de la SAT), sin guion. */
    public static String nuevo() {
        String cuerpo = String.valueOf(10_000_000L + SIGUIENTE.incrementAndGet() % 89_999_999L);
        return cuerpo + verificador(cuerpo);
    }

    /** El mismo NIT con guion antes del verificador, como lo escribe el cajero. */
    public static String conGuion(String nit) {
        return nit.substring(0, nit.length() - 1) + "-" + nit.charAt(nit.length() - 1);
    }

    /** El NIT con otro verificador: no es válido. */
    public static String invalido(String nit) {
        char verificador = nit.charAt(nit.length() - 1);
        return nit.substring(0, nit.length() - 1) + (verificador == '1' ? '2' : '1');
    }

    private static char verificador(String cuerpo) {
        int suma = 0;
        for (int i = 0; i < cuerpo.length(); i++) {
            suma += (cuerpo.charAt(i) - '0') * (cuerpo.length() + 1 - i);
        }
        int resto = (11 - suma % 11) % 11;
        return resto == 10 ? 'K' : (char) ('0' + resto);
    }
}
