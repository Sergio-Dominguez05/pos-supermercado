import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { CLAVE_EMISOR, guardarEmisor, obtenerEmisor, type AfiliacionIva, type Emisor } from '../../api/emisor'
import { nitValido, normalizarNit } from '../clientes/nit'
import { useSesion } from '../login/sesion'
import { NavConfiguracion } from './NavConfiguracion'
import { AVISO_EMISOR_GUARDADO, NIT_INVALIDO } from './textos'

/** HU-12.01: los datos fiscales del emisor. Solo para quien puede modificar la configuración (HU-12.15 crit. 3). */
export function PaginaEmisor() {
  const { data: usuario } = useSesion()
  if (!usuario) return null

  return (
    <main className="pagina">
      <h1>Datos del emisor</h1>
      <NavConfiguracion actual="Emisor" />
      {usuario.permisos.includes('CONFIGURACION_MODIFICAR') ? (
        <CargaEmisor />
      ) : (
        <p className="tarjeta error">No tiene permiso para modificar la configuración.</p>
      )}
    </main>
  )
}

function CargaEmisor() {
  const { data: emisor, error, isPending } = useQuery({ queryKey: CLAVE_EMISOR, queryFn: obtenerEmisor })

  if (isPending) return <p className="tarjeta">Cargando los datos del emisor…</p>
  if (error) return <p className="tarjeta error">{error.message}</p>
  return <FormularioEmisor guardado={emisor} />
}

/**
 * - Crit. 1: NIT, razón social, nombre comercial, dirección fiscal, teléfono y correo (los dos últimos opcionales).
 * - Crit. 2: el NIT se valida con el algoritmo de la SAT (HU-05.03) antes de enviar, y el servidor lo vuelve a validar.
 * - Crit. 3 y 4: las facturas usan estos datos y guardan una copia al emitirse, así que un cambio no las altera.
 */
export function FormularioEmisor({ guardado }: { guardado: Emisor }) {
  const clienteConsultas = useQueryClient()
  const [nit, setNit] = useState(guardado.nit ?? '')
  const [razonSocial, setRazonSocial] = useState(guardado.razonSocial ?? '')
  const [nombreComercial, setNombreComercial] = useState(guardado.nombreComercial ?? '')
  const [direccionFiscal, setDireccionFiscal] = useState(guardado.direccionFiscal ?? '')
  const [telefono, setTelefono] = useState(guardado.telefono ?? '')
  const [correo, setCorreo] = useState(guardado.correo ?? '')
  const [afiliacionIva, setAfiliacionIva] = useState<AfiliacionIva | undefined>(guardado.afiliacionIva ?? undefined)
  const [aviso, setAviso] = useState<string>()
  const [nitRevisado, setNitRevisado] = useState(false)

  const guardadoMut = useMutation({
    mutationFn: () =>
      guardarEmisor({ nit, razonSocial, nombreComercial, direccionFiscal, telefono, correo, afiliacionIva }),
    onMutate: () => setAviso(undefined),
    onSuccess: (actualizado) => {
      clienteConsultas.setQueryData(CLAVE_EMISOR, actualizado)
      setNit(actualizado.nit ?? nit)
      setAviso(AVISO_EMISOR_GUARDADO)
    },
  })
  const nitConError = nitRevisado && nit.trim() !== '' && !nitValido(nit)

  return (
    <form
      className="tarjeta formulario"
      aria-label="Datos del emisor"
      onSubmit={(evento) => {
        evento.preventDefault()
        setNitRevisado(true)
        if (nitValido(nit)) guardadoMut.mutate()
      }}
    >
      {aviso && (
        <p className="exito" role="status">
          {aviso}
        </p>
      )}
      <label>
        NIT
        <input
          name="nit"
          required
          maxLength={20}
          autoComplete="off"
          aria-invalid={nitConError}
          value={nit}
          onChange={(evento) => setNit(evento.target.value)}
          onBlur={() => {
            setNitRevisado(true)
            if (nitValido(nit)) setNit(normalizarNit(nit))
          }}
        />
        <small>Con su dígito verificador, que puede ser K. Se guarda sin guiones ni espacios.</small>
      </label>
      {nitConError && (
        <p className="error" role="alert">
          {NIT_INVALIDO}
        </p>
      )}
      <label>
        Razón social
        <input
          name="razonSocial"
          required
          maxLength={200}
          value={razonSocial}
          onChange={(evento) => setRazonSocial(evento.target.value)}
        />
      </label>
      <label>
        Nombre comercial
        <input
          name="nombreComercial"
          required
          maxLength={200}
          value={nombreComercial}
          onChange={(evento) => setNombreComercial(evento.target.value)}
        />
      </label>
      <label>
        Dirección fiscal
        <input
          name="direccionFiscal"
          required
          maxLength={300}
          value={direccionFiscal}
          onChange={(evento) => setDireccionFiscal(evento.target.value)}
        />
      </label>
      <label>
        Teléfono (opcional)
        <input
          name="telefono"
          type="tel"
          maxLength={20}
          pattern="[0-9+ \-]{8,20}"
          title="De 8 a 20 dígitos"
          value={telefono}
          onChange={(evento) => setTelefono(evento.target.value)}
        />
      </label>
      <label>
        Correo (opcional)
        <input
          name="correo"
          type="email"
          maxLength={254}
          value={correo}
          onChange={(evento) => setCorreo(evento.target.value)}
        />
      </label>
      <fieldset>
        <legend>Afiliación al IVA</legend>
        <label>
          <input
            type="radio"
            name="afiliacionIva"
            value="GEN"
            required
            checked={afiliacionIva === 'GEN'}
            onChange={() => setAfiliacionIva('GEN')}
          />{' '}
          Régimen General
        </label>
        <label>
          <input
            type="radio"
            name="afiliacionIva"
            value="PEQ"
            checked={afiliacionIva === 'PEQ'}
            onChange={() => setAfiliacionIva('PEQ')}
          />{' '}
          Pequeño Contribuyente
        </label>
        <small>La pide el documento de la factura. Esta versión factura en el régimen General.</small>
      </fieldset>
      <p className="pendiente">
        Estos datos aparecen en las facturas. Cada factura guarda una copia al emitirse: un cambio aquí no altera las
        facturas ya emitidas.
      </p>

      {guardadoMut.error && (
        <p className="error" role="alert">
          {guardadoMut.error.message}
        </p>
      )}
      <button type="submit" disabled={guardadoMut.isPending}>
        {guardadoMut.isPending ? 'Guardando…' : 'Guardar'}
      </button>
      {guardado.modificadoEn && (
        <p className="pendiente">Última modificación: {new Date(guardado.modificadoEn).toLocaleString('es-GT')}.</p>
      )}
    </form>
  )
}
