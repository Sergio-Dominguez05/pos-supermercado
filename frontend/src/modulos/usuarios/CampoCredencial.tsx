import { useState } from 'react'
import type { Rol } from '../../api/usuarios'

/**
 * Credencial temporal según el rol: PIN de 4 a 6 dígitos para Cajero, contraseña para los demás.
 * Quien la asigna debe entregarla al usuario: la casilla "Mostrar" permite revisarla antes de guardar.
 */
export function CampoCredencial({ rol, valor, onChange }: { rol: Rol; valor: string; onChange: (v: string) => void }) {
  const [mostrar, setMostrar] = useState(false)
  const esPin = rol === 'CAJERO'

  return (
    <>
      {esPin ? (
        <label>
          PIN temporal
          <input
            type={mostrar ? 'text' : 'password'}
            name="credencialTemporal"
            inputMode="numeric"
            pattern="\d{4,6}"
            title="De 4 a 6 dígitos"
            autoComplete="off"
            required
            minLength={4}
            maxLength={6}
            value={valor}
            onChange={(evento) => onChange(evento.target.value.replace(/\D/g, ''))}
          />
          <small>Numérico, de 4 a 6 dígitos.</small>
        </label>
      ) : (
        <label>
          Contraseña temporal
          <input
            type={mostrar ? 'text' : 'password'}
            name="credencialTemporal"
            autoComplete="new-password"
            required
            minLength={8}
            maxLength={128}
            value={valor}
            onChange={(evento) => onChange(evento.target.value)}
          />
          <small>Al menos 8 caracteres, con letras y números.</small>
        </label>
      )}
      <label className="casilla">
        <input type="checkbox" checked={mostrar} onChange={(evento) => setMostrar(evento.target.checked)} />
        Mostrar {esPin ? 'el PIN' : 'la contraseña'}
      </label>
    </>
  )
}
