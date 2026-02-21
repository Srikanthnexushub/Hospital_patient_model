import { useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { useForm, useFieldArray } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { useCreateInvoice } from '../hooks/useInvoices.js'
import InlineError from '../components/common/InlineError.jsx'

const lineItemSchema = z.object({
  serviceCode:  z.string().min(1, 'Required').max(20),
  description:  z.string().min(1, 'Required'),
  quantity:     z.coerce.number().int().min(1, 'Min 1'),
  unitPrice:    z.coerce.number().min(0.01, 'Must be > 0'),
})

const schema = z.object({
  appointmentId:   z.string().min(1, 'Appointment ID is required'),
  lineItems:       z.array(lineItemSchema).min(1, 'At least one line item is required'),
  discountPercent: z.coerce.number().min(0).max(100).optional().or(z.literal('')),
  notes:           z.string().max(1000).optional(),
})

const EMPTY_LINE_ITEM = { serviceCode: '', description: '', quantity: 1, unitPrice: '' }

function fmt2(val) {
  const n = Number(val)
  return isNaN(n) ? '0.00' : n.toFixed(2)
}

export default function InvoiceCreatePage() {
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const prefilledApptId = searchParams.get('appointmentId') ?? ''

  const { mutate: create, isPending, error } = useCreateInvoice()

  const { register, handleSubmit, control, watch, formState: { errors } } = useForm({
    resolver: zodResolver(schema),
    defaultValues: {
      appointmentId:   prefilledApptId,
      lineItems:       [{ ...EMPTY_LINE_ITEM }],
      discountPercent: '',
      notes:           '',
    },
  })

  const { fields, append, remove } = useFieldArray({ control, name: 'lineItems' })

  // Live totals
  const watchedItems    = watch('lineItems')
  const watchedDiscount = watch('discountPercent')
  const totalAmount = (watchedItems ?? []).reduce((sum, item) => {
    const qty   = Number(item.quantity)  || 0
    const price = Number(item.unitPrice) || 0
    return sum + qty * price
  }, 0)
  const discountPct    = Math.min(Math.max(Number(watchedDiscount) || 0, 0), 100)
  const discountAmount = totalAmount * discountPct / 100
  const netAmount      = totalAmount - discountAmount

  const serverError = error?.response?.data?.message ?? error?.message

  function onSubmit(data) {
    const payload = {
      appointmentId:   data.appointmentId,
      lineItems:       data.lineItems.map(i => ({
        serviceCode: i.serviceCode,
        description: i.description,
        quantity:    Number(i.quantity),
        unitPrice:   Number(i.unitPrice),
      })),
      discountPercent: data.discountPercent !== '' ? Number(data.discountPercent) : undefined,
      notes:           data.notes || undefined,
    }
    create(payload, {
      onSuccess: inv => navigate(`/invoices/${inv.invoiceId}`, {
        state: { message: `Invoice ${inv.invoiceId} created successfully.` },
      }),
    })
  }

  return (
    <div className="max-w-3xl mx-auto px-4 py-8">
      <div className="mb-4">
        <button onClick={() => navigate(-1)} className="text-sm text-blue-600 hover:underline">
          ← Back
        </button>
      </div>

      <h1 className="text-2xl font-bold text-gray-900 mb-6">Create Invoice</h1>

      {serverError && (
        <div className="mb-4 rounded-md bg-red-50 border border-red-200 p-4 text-red-700 text-sm">
          {serverError}
        </div>
      )}

      <form onSubmit={handleSubmit(onSubmit)} className="space-y-6">

        {/* Appointment ID */}
        <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-6">
          <h2 className="text-base font-semibold text-gray-900 mb-4">Appointment</h2>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">
              Appointment ID <span className="text-red-500">*</span>
            </label>
            <input
              {...register('appointmentId')}
              readOnly={!!prefilledApptId}
              className={`w-full border rounded px-3 py-2 text-sm font-mono ${
                errors.appointmentId ? 'border-red-400' : 'border-gray-300'
              } ${prefilledApptId ? 'bg-gray-50 text-gray-600' : ''}`}
              placeholder="APT20260001"
            />
            <InlineError error={errors.appointmentId} />
          </div>
        </div>

        {/* Line Items */}
        <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-6">
          <div className="flex items-center justify-between mb-4">
            <h2 className="text-base font-semibold text-gray-900">Line Items</h2>
            <button
              type="button"
              onClick={() => append({ ...EMPTY_LINE_ITEM })}
              className="text-sm text-blue-600 hover:text-blue-800 font-medium"
            >
              + Add Item
            </button>
          </div>

          {errors.lineItems?.root && (
            <p className="text-red-600 text-xs mb-3">{errors.lineItems.root.message}</p>
          )}
          {errors.lineItems?.message && (
            <p className="text-red-600 text-xs mb-3">{errors.lineItems.message}</p>
          )}

          <div className="space-y-3">
            {fields.map((field, idx) => (
              <div key={field.id} className="grid grid-cols-12 gap-2 items-start">
                <div className="col-span-2">
                  <label className="block text-xs font-medium text-gray-500 mb-1">Code</label>
                  <input
                    {...register(`lineItems.${idx}.serviceCode`)}
                    className={`w-full border rounded px-2 py-1.5 text-sm ${errors.lineItems?.[idx]?.serviceCode ? 'border-red-400' : 'border-gray-300'}`}
                    placeholder="SVC001"
                  />
                  <InlineError error={errors.lineItems?.[idx]?.serviceCode} />
                </div>
                <div className="col-span-4">
                  <label className="block text-xs font-medium text-gray-500 mb-1">Description</label>
                  <input
                    {...register(`lineItems.${idx}.description`)}
                    className={`w-full border rounded px-2 py-1.5 text-sm ${errors.lineItems?.[idx]?.description ? 'border-red-400' : 'border-gray-300'}`}
                    placeholder="Consultation fee"
                  />
                  <InlineError error={errors.lineItems?.[idx]?.description} />
                </div>
                <div className="col-span-2">
                  <label className="block text-xs font-medium text-gray-500 mb-1">Qty</label>
                  <input
                    type="number" min="1"
                    {...register(`lineItems.${idx}.quantity`)}
                    className={`w-full border rounded px-2 py-1.5 text-sm ${errors.lineItems?.[idx]?.quantity ? 'border-red-400' : 'border-gray-300'}`}
                  />
                  <InlineError error={errors.lineItems?.[idx]?.quantity} />
                </div>
                <div className="col-span-3">
                  <label className="block text-xs font-medium text-gray-500 mb-1">Unit Price ($)</label>
                  <input
                    type="number" min="0.01" step="0.01"
                    {...register(`lineItems.${idx}.unitPrice`)}
                    className={`w-full border rounded px-2 py-1.5 text-sm ${errors.lineItems?.[idx]?.unitPrice ? 'border-red-400' : 'border-gray-300'}`}
                  />
                  <InlineError error={errors.lineItems?.[idx]?.unitPrice} />
                </div>
                <div className="col-span-1 flex items-end pb-1">
                  {fields.length > 1 && (
                    <button
                      type="button"
                      onClick={() => remove(idx)}
                      className="text-red-500 hover:text-red-700 text-lg leading-none mt-5"
                      title="Remove"
                    >
                      ×
                    </button>
                  )}
                </div>
              </div>
            ))}
          </div>

          {/* Live total */}
          <div className="mt-4 pt-4 border-t border-gray-100 space-y-1 text-sm text-right">
            <div className="flex justify-between text-gray-600">
              <span>Subtotal</span>
              <span>${fmt2(totalAmount)}</span>
            </div>
            {discountPct > 0 && (
              <div className="flex justify-between text-amber-700">
                <span>Discount ({discountPct}%)</span>
                <span>− ${fmt2(discountAmount)}</span>
              </div>
            )}
            <div className="flex justify-between font-semibold text-gray-900">
              <span>Net Amount</span>
              <span>${fmt2(netAmount)}</span>
            </div>
          </div>
        </div>

        {/* Discount & Notes */}
        <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-6 space-y-4">
          <h2 className="text-base font-semibold text-gray-900">Additional Details</h2>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">
                Discount (%)
              </label>
              <input
                type="number" min="0" max="100" step="0.01"
                {...register('discountPercent')}
                className={`w-full border rounded px-3 py-2 text-sm ${errors.discountPercent ? 'border-red-400' : 'border-gray-300'}`}
                placeholder="0"
              />
              <InlineError error={errors.discountPercent} />
            </div>
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Notes (optional)</label>
            <textarea
              {...register('notes')}
              rows={3}
              className="w-full border border-gray-300 rounded px-3 py-2 text-sm"
              placeholder="Additional billing notes…"
            />
          </div>
        </div>

        <div className="flex gap-3">
          <button type="submit" disabled={isPending} className="btn-primary">
            {isPending ? 'Creating…' : 'Create Invoice'}
          </button>
          <button type="button" onClick={() => navigate(-1)} className="btn-secondary">
            Cancel
          </button>
        </div>
      </form>
    </div>
  )
}
