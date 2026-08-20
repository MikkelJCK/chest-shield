package com.mikkeljck.reinforcedchests.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Hashing de contrasenas con PBKDF2-HmacSHA256.
 *
 * La contrasena en claro nunca se guarda. Se guarda un salt aleatorio por cofre
 * mas el hash resultante. El salt evita que dos cofres con la misma clave tengan
 * el mismo hash, y las 100.000 iteraciones encarecen la fuerza bruta.
 *
 * Todo viene incluido en el JDK: no agrega ninguna dependencia al mod.
 */
public final class HashClave {

	private static final String ALGORITMO = "PBKDF2WithHmacSHA256";
	private static final int ITERACIONES = 100_000;
	private static final int BITS = 256;
	private static final int BYTES_SALT = 16;

	private static final SecureRandom ALEATORIO = new SecureRandom();

	private HashClave() {
	}

	/** Genera un salt nuevo, codificado en Base64 para poder guardarlo como texto. */
	public static String nuevoSalt() {
		byte[] salt = new byte[BYTES_SALT];
		ALEATORIO.nextBytes(salt);
		return Base64.getEncoder().encodeToString(salt);
	}

	public static String calcular(final String clave, final String saltBase64) {
		PBEKeySpec spec = new PBEKeySpec(
				clave.toCharArray(),
				Base64.getDecoder().decode(saltBase64),
				ITERACIONES,
				BITS);
		try {
			byte[] hash = SecretKeyFactory.getInstance(ALGORITMO).generateSecret(spec).getEncoded();
			return Base64.getEncoder().encodeToString(hash);
		} catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
			throw new IllegalStateException("No se pudo calcular el hash de la clave", e);
		} finally {
			spec.clearPassword();
		}
	}

	/**
	 * Comparacion en tiempo constante. Una comparacion normal con equals() tarda
	 * mas cuanto mas caracteres coinciden, y eso se puede medir para adivinar la
	 * clave carater a caracter.
	 */
	public static boolean coincide(final String clave, final String saltBase64, final String hashEsperado) {
		if (saltBase64.isEmpty() || hashEsperado.isEmpty()) {
			return false;
		}
		String calculado = calcular(clave, saltBase64);
		return MessageDigest.isEqual(
				calculado.getBytes(StandardCharsets.UTF_8),
				hashEsperado.getBytes(StandardCharsets.UTF_8));
	}
}
