import { useParams, Link } from 'react-router-dom'
import { useMedicalSummary } from '../hooks/useEmr.js'
import LoadingSpinner from '../components/common/LoadingSpinner.jsx'

// ── Utility helpers ────────────────────────────────────────────────────────────

function relativeDate(isoDate) {
  if (!isoDate) return null
  const diff = Math.floor((Date.now() - new Date(isoDate)) / 86_400_000)
  if (diff === 0) return 'Today'
  if (diff === 1) return 'Yesterday'
  if (diff < 7)  return `${diff}d ago`
  if (diff < 30) return `${Math.floor(diff / 7)}w ago`
  if (diff < 365) return `${Math.floor(diff / 30)}mo ago`
  return `${Math.floor(diff / 365)}y ago`
}

function futureDays(isoDate) {
  if (!isoDate) return null
  const diff = Math.ceil((new Date(isoDate) - Date.now()) / 86_400_000)
  if (diff === 0) return 'Today'
  if (diff === 1) return 'Tomorrow'
  return `in ${diff} days`
}

function medicationAge(startDate) {
  if (!startDate) return null
  return Math.floor((Date.now() - new Date(startDate)) / 86_400_000)
}

function calcBMI(weight, height) {
  if (!weight || !height) return null
  const hm  = Number(height) / 100
  const bmi = Number(weight) / (hm * hm)
  const [cat, cls] = bmi < 18.5 ? ['Underweight', 'text-blue-600']
                   : bmi < 25   ? ['Normal',       'text-green-600']
                   : bmi < 30   ? ['Overweight',   'text-yellow-600']
                   :              ['Obese',         'text-red-600']
  return { value: bmi.toFixed(1), cat, cls }
}

// Color the vital value based on clinical normal ranges
function vitalCls(key, val) {
  if (val == null) return 'text-gray-400'
  const v = Number(val)
  switch (key) {
    case 'sys':  return v < 120 ? 'text-green-600' : v < 130 ? 'text-yellow-600' : v < 140 ? 'text-orange-500' : 'text-red-600'
    case 'dia':  return v < 80  ? 'text-green-600' : v < 90  ? 'text-yellow-600' : 'text-red-600'
    case 'hr':   return v >= 60 && v <= 100 ? 'text-green-600' : v > 100 ? 'text-red-600' : 'text-yellow-600'
    case 'temp': return v < 36.1 ? 'text-blue-600' : v <= 37.2 ? 'text-green-600' : v <= 38.0 ? 'text-yellow-600' : 'text-red-600'
    case 'spo2': return v >= 95  ? 'text-green-600' : v >= 90 ? 'text-yellow-600' : 'text-red-600'
    case 'rr':   return v >= 12 && v <= 20 ? 'text-green-600' : v > 24 ? 'text-red-600' : 'text-yellow-600'
    default:     return 'text-gray-700'
  }
}

function trendArrow(curr, prev) {
  if (curr == null || prev == null) return null
  const diff = Number(curr) - Number(prev)
  if (Math.abs(diff) < 1) return { sym: '→', cls: 'text-gray-400' }
  return diff > 0
    ? { sym: '↑', cls: 'text-orange-400' }
    : { sym: '↓', cls: 'text-blue-400' }
}

// ── SVG Sparkline ──────────────────────────────────────────────────────────────

function Sparkline({ values, colour = '#6366f1', w = 80, h = 24 }) {
  const nums = values.filter(v => v != null).map(Number)
  if (nums.length < 2) return <span className="text-xs text-gray-300">—</span>
  const mn  = Math.min(...nums)
  const mx  = Math.max(...nums)
  const rng = mx - mn || 1
  const pts = nums.map((v, i) =>
    `${(i / (nums.length - 1)) * w},${(h - 3) - ((v - mn) / rng) * (h - 6) + 1}`
  ).join(' ')
  const latest = nums[nums.length - 1]
  const cx = w
  const cy = (h - 3) - ((latest - mn) / rng) * (h - 6) + 1
  return (
    <svg width={w + 6} height={h} className="inline-block align-middle overflow-visible">
      <polyline points={pts} fill="none" stroke={colour}
        strokeWidth="1.5" strokeLinejoin="round" strokeLinecap="round" />
      <circle cx={cx} cy={cy} r="2.5" fill={colour} />
    </svg>
  )
}

// ── Small shared components ────────────────────────────────────────────────────

const SEVERITY_CLS = {
  MILD:             'bg-green-100 text-green-800',
  MODERATE:         'bg-yellow-100 text-yellow-800',
  SEVERE:           'bg-orange-100 text-orange-800',
  LIFE_THREATENING: 'bg-red-100 text-red-900 ring-1 ring-red-400',
}

function SeverityBadge({ severity }) {
  return (
    <span className={`inline-flex items-center px-2 py-0.5 rounded-full text-xs font-semibold ${SEVERITY_CLS[severity] ?? 'bg-gray-100 text-gray-600'}`}>
      {severity?.replace(/_/g, ' ')}
    </span>
  )
}

function SectionCard({ title, children, accent }) {
  const bar = accent ?? 'bg-blue-500'
  return (
    <div className="bg-white rounded-xl shadow-sm border border-gray-200 overflow-hidden">
      <div className={`h-1 ${bar}`} />
      <div className="p-5">
        <h2 className="text-xs font-bold text-gray-500 uppercase tracking-wider mb-4">{title}</h2>
        {children}
      </div>
    </div>
  )
}

function EmptyState({ message }) {
  return <p className="text-sm text-gray-400 italic">{message}</p>
}

// ── Stat cards ────────────────────────────────────────────────────────────────

function StatCard({ count, label, color, alert }) {
  const colors = {
    orange: 'border-orange-200 bg-gradient-to-br from-orange-50 to-white text-orange-700',
    blue:   'border-blue-200   bg-gradient-to-br from-blue-50   to-white text-blue-700',
    purple: 'border-purple-200 bg-gradient-to-br from-purple-50 to-white text-purple-700',
    teal:   'border-teal-200   bg-gradient-to-br from-teal-50   to-white text-teal-700',
  }
  return (
    <div className={`relative rounded-xl border p-4 text-center shadow-sm ${colors[color]}`}>
      {alert && (
        <span className="absolute -top-1.5 -right-1.5 flex h-4 w-4 items-center justify-center rounded-full bg-red-500 text-[9px] font-bold text-white">!</span>
      )}
      <p className="text-3xl font-black">{count}</p>
      <p className="text-xs font-medium mt-1 opacity-75">{label}</p>
    </div>
  )
}

// ── Vitals section ────────────────────────────────────────────────────────────

function VitalsSection({ vitals, bmi }) {
  if (vitals.length === 0) {
    return (
      <SectionCard title="Recent Vitals" accent="bg-indigo-400">
        <EmptyState message="No vitals recorded yet." />
      </SectionCard>
    )
  }

  const lv  = vitals[0]
  const pv  = vitals[1] ?? null

  const sysVals  = [...vitals].reverse().map(v => v.bloodPressureSystolic)
  const hrVals   = [...vitals].reverse().map(v => v.heartRate)

  return (
    <SectionCard title="Recent Vitals — Last 5 Recordings" accent="bg-indigo-400">

      {/* Sparklines + BMI row */}
      <div className="flex flex-wrap items-end gap-8 mb-5 pb-4 border-b border-gray-100">
        <div className="flex flex-col gap-1">
          <span className="text-[10px] font-semibold text-gray-400 uppercase tracking-wide">Systolic BP Trend</span>
          <div className="flex items-end gap-2">
            <Sparkline values={sysVals} colour="#ef4444" w={80} h={28} />
            {lv.bloodPressureSystolic != null && (
              <span className={`text-sm font-bold ${vitalCls('sys', lv.bloodPressureSystolic)}`}>
                {lv.bloodPressureSystolic}
              </span>
            )}
          </div>
        </div>
        <div className="flex flex-col gap-1">
          <span className="text-[10px] font-semibold text-gray-400 uppercase tracking-wide">Heart Rate Trend</span>
          <div className="flex items-end gap-2">
            <Sparkline values={hrVals} colour="#6366f1" w={80} h={28} />
            {lv.heartRate != null && (
              <span className={`text-sm font-bold ${vitalCls('hr', lv.heartRate)}`}>
                {lv.heartRate} bpm
              </span>
            )}
          </div>
        </div>
        {bmi && (
          <div className="flex flex-col gap-0.5 ml-auto">
            <span className="text-[10px] font-semibold text-gray-400 uppercase tracking-wide">BMI (latest)</span>
            <span className={`text-2xl font-black ${bmi.cls}`}>{bmi.value}</span>
            <span className={`text-xs font-semibold ${bmi.cls}`}>{bmi.cat}</span>
          </div>
        )}
      </div>

      {/* Vitals table */}
      <div className="overflow-x-auto -mx-1">
        <table className="min-w-full text-sm">
          <thead>
            <tr>
              {['Date', 'BP (mmHg)', 'HR (bpm)', 'Temp (°C)', 'SpO₂', 'RR', 'Weight', 'Recorded by'].map(h => (
                <th key={h} className="pb-2 pr-4 text-left text-[10px] font-semibold text-gray-400 uppercase tracking-wide whitespace-nowrap">{h}</th>
              ))}
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-50">
            {vitals.map((v, idx) => {
              const isLatest = idx === 0
              return (
                <tr key={v.id ?? idx} className={isLatest ? 'bg-indigo-50/30' : ''}>
                  <td className="py-2 pr-4 text-xs whitespace-nowrap">
                    <span className="text-gray-600">{v.recordedAt?.slice(0, 10)}</span>
                    {isLatest && (
                      <span className="ml-1 text-[9px] font-bold text-indigo-600 bg-indigo-100 px-1.5 py-0.5 rounded-full">LATEST</span>
                    )}
                  </td>

                  {/* BP */}
                  <td className="py-2 pr-4 whitespace-nowrap font-mono text-xs">
                    {v.bloodPressureSystolic != null ? (
                      <span>
                        <span className={`font-bold ${vitalCls('sys', v.bloodPressureSystolic)}`}>{v.bloodPressureSystolic}</span>
                        <span className="text-gray-300">/</span>
                        <span className={vitalCls('dia', v.bloodPressureDiastolic)}>{v.bloodPressureDiastolic}</span>
                        {isLatest && trendArrow(v.bloodPressureSystolic, pv?.bloodPressureSystolic) && (
                          <span className={`ml-1 ${trendArrow(v.bloodPressureSystolic, pv?.bloodPressureSystolic).cls}`}>
                            {trendArrow(v.bloodPressureSystolic, pv?.bloodPressureSystolic).sym}
                          </span>
                        )}
                      </span>
                    ) : <span className="text-gray-300">—</span>}
                  </td>

                  {/* HR */}
                  <td className="py-2 pr-4 whitespace-nowrap font-mono text-xs">
                    {v.heartRate != null ? (
                      <span>
                        <span className={`font-bold ${vitalCls('hr', v.heartRate)}`}>{v.heartRate}</span>
                        {isLatest && trendArrow(v.heartRate, pv?.heartRate) && (
                          <span className={`ml-1 ${trendArrow(v.heartRate, pv?.heartRate).cls}`}>
                            {trendArrow(v.heartRate, pv?.heartRate).sym}
                          </span>
                        )}
                      </span>
                    ) : <span className="text-gray-300">—</span>}
                  </td>

                  {/* Temp */}
                  <td className="py-2 pr-4 font-mono text-xs">
                    {v.temperature != null
                      ? <span className={`font-bold ${vitalCls('temp', v.temperature)}`}>{v.temperature}</span>
                      : <span className="text-gray-300">—</span>}
                  </td>

                  {/* SpO2 */}
                  <td className="py-2 pr-4 font-mono text-xs">
                    {v.oxygenSaturation != null
                      ? <span className={`font-bold ${vitalCls('spo2', v.oxygenSaturation)}`}>{v.oxygenSaturation}%</span>
                      : <span className="text-gray-300">—</span>}
                  </td>

                  {/* RR */}
                  <td className="py-2 pr-4 font-mono text-xs">
                    {v.respiratoryRate != null
                      ? <span className={`font-bold ${vitalCls('rr', v.respiratoryRate)}`}>{v.respiratoryRate}/min</span>
                      : <span className="text-gray-300">—</span>}
                  </td>

                  {/* Weight */}
                  <td className="py-2 pr-4 text-xs text-gray-600">
                    {v.weight != null ? `${v.weight} kg` : <span className="text-gray-300">—</span>}
                  </td>

                  {/* Recorded by */}
                  <td className="py-2 text-xs text-gray-400">{v.recordedBy}</td>
                </tr>
              )
            })}
          </tbody>
        </table>
      </div>

      {/* Legend */}
      <div className="mt-4 pt-3 border-t border-gray-100 flex flex-wrap gap-4 text-xs">
        <span className="text-green-600 font-medium">● Normal</span>
        <span className="text-yellow-600 font-medium">● Borderline</span>
        <span className="text-orange-500 font-medium">● Elevated</span>
        <span className="text-red-600 font-medium">● Critical</span>
        <span className="text-blue-600 font-medium">● Low / Hypothermia</span>
        <span className="ml-auto text-gray-400">↑↓ vs previous reading</span>
      </div>
    </SectionCard>
  )
}

// ── Problems section ──────────────────────────────────────────────────────────

const SEVERITY_BORDER = {
  MILD:             'border-green-300',
  MODERATE:         'border-yellow-300',
  SEVERE:           'border-orange-400',
  LIFE_THREATENING: 'border-red-500',
}

function ProblemsSection({ problems }) {
  return (
    <SectionCard title={`Active Problems · ${problems.length}`} accent="bg-orange-400">
      {problems.length === 0
        ? <EmptyState message="No active problems on record." />
        : (
          <div className="space-y-3">
            {problems.map(p => (
              <div key={p.id} className={`border-l-4 pl-3 py-1 rounded-r ${SEVERITY_BORDER[p.severity] ?? 'border-gray-200'}`}>
                <div className="flex items-start justify-between gap-2 flex-wrap">
                  <p className="text-sm font-semibold text-gray-900">{p.title}</p>
                  <SeverityBadge severity={p.severity} />
                </div>
                <div className="flex flex-wrap gap-2 mt-1">
                  {p.icdCode && (
                    <span className="inline-block text-[10px] font-mono bg-gray-100 text-gray-500 px-1.5 py-0.5 rounded">
                      ICD-10: {p.icdCode}
                    </span>
                  )}
                  {p.onsetDate && (
                    <span className="text-[10px] text-gray-400">Since {p.onsetDate}</span>
                  )}
                </div>
                {p.description && (
                  <p className="text-xs text-gray-500 mt-1">{p.description}</p>
                )}
              </div>
            ))}
          </div>
        )
      }
    </SectionCard>
  )
}

// ── Medications section ───────────────────────────────────────────────────────

const ROUTE_ICON = { ORAL: '💊', IV: '💉', IM: '💉', TOPICAL: '🧴', INHALED: '🌬️', OTHER: '⬛' }

function MedicationsSection({ medications }) {
  return (
    <SectionCard title={`Active Medications · ${medications.length}`} accent="bg-blue-400">
      {medications.length === 0
        ? <EmptyState message="No active medications." />
        : (
          <div className="space-y-3">
            {medications.map(m => {
              const days = medicationAge(m.startDate)
              return (
                <div key={m.id} className="rounded-lg bg-gray-50 border border-gray-100 p-3">
                  <div className="flex items-start justify-between gap-2">
                    <div>
                      <p className="text-sm font-semibold text-gray-900">
                        {ROUTE_ICON[m.route] ?? ''} {m.medicationName}
                      </p>
                      {m.genericName && (
                        <p className="text-xs text-gray-400">({m.genericName})</p>
                      )}
                    </div>
                    <span className="shrink-0 text-xs font-medium bg-blue-50 text-blue-700 border border-blue-200 px-2 py-0.5 rounded-full">
                      {m.route}
                    </span>
                  </div>
                  <p className="text-xs text-gray-600 mt-1.5">{m.dosage} · {m.frequency}</p>
                  {m.indication && (
                    <p className="text-xs text-gray-400 mt-0.5">For: {m.indication}</p>
                  )}
                  <div className="flex items-center justify-between mt-2 pt-2 border-t border-gray-100">
                    <span className="text-xs text-gray-400">Rx: {m.prescribedBy}</span>
                    {days != null && (
                      <span className={`text-xs font-semibold px-2 py-0.5 rounded-full ${
                        days > 90 ? 'bg-purple-50 text-purple-700' : 'bg-green-50 text-green-700'
                      }`}>
                        {days === 0 ? 'Started today' : `${days}d on medication`}
                      </span>
                    )}
                  </div>
                </div>
              )
            })}
          </div>
        )
      }
    </SectionCard>
  )
}

// ── Allergies section ─────────────────────────────────────────────────────────

function AllergiesSection({ allergies }) {
  const critical = allergies.filter(a => a.severity === 'LIFE_THREATENING')
  const others   = allergies.filter(a => a.severity !== 'LIFE_THREATENING')

  return (
    <SectionCard title={`Known Allergies · ${allergies.length}`} accent="bg-purple-400">
      {allergies.length === 0 ? (
        <EmptyState message="No known allergies on record." />
      ) : (
        <>
          {critical.length > 0 && (
            <div className="mb-4">
              <p className="text-[10px] font-bold text-red-600 uppercase tracking-widest mb-2">
                ⚠ Life-Threatening
              </p>
              <div className="space-y-2">
                {critical.map(a => (
                  <div key={a.id} className="flex items-center gap-3 rounded-lg bg-red-50 border border-red-200 p-3">
                    <div className="flex-1">
                      <p className="text-sm font-bold text-red-900">{a.substance}</p>
                      <p className="text-xs text-red-700 mt-0.5">{a.reaction}</p>
                    </div>
                    <span className="shrink-0 text-xs bg-red-100 text-red-700 border border-red-200 px-2 py-0.5 rounded-full font-medium">{a.type}</span>
                  </div>
                ))}
              </div>
            </div>
          )}
          {others.length > 0 && (
            <div className="divide-y divide-gray-100">
              {others.map(a => (
                <div key={a.id} className="py-2.5 flex items-start gap-3">
                  <SeverityBadge severity={a.severity} />
                  <div className="flex-1 min-w-0">
                    <p className="text-sm font-medium text-gray-900">
                      {a.substance}
                      <span className="ml-2 text-xs text-gray-400 font-normal">{a.type}</span>
                    </p>
                    <p className="text-xs text-gray-500 mt-0.5">{a.reaction}</p>
                  </div>
                </div>
              ))}
            </div>
          )}
        </>
      )}
    </SectionCard>
  )
}

// ── Main page ─────────────────────────────────────────────────────────────────

export default function MedicalSummaryPage() {
  const { patientId } = useParams()
  const { data: s, isLoading, isError, error } = useMedicalSummary(patientId)

  if (isLoading) return <div className="max-w-4xl mx-auto px-4 py-8"><LoadingSpinner /></div>

  if (isError) {
    const is403 = error?.response?.status === 403
    const is404 = error?.response?.status === 404
    return (
      <div className="max-w-4xl mx-auto px-4 py-8">
        <div className="rounded-md bg-red-50 border border-red-200 p-4 text-red-700">
          {is403 ? 'You do not have permission to view this summary.'
           : is404 ? 'Patient not found.'
           : 'Failed to load medical summary.'}
        </div>
        <Link to={`/patients/${patientId}`} className="inline-block mt-4 text-sm text-blue-600 hover:underline">
          ← Back to Patient Profile
        </Link>
      </div>
    )
  }

  const hasCritical = s.allergies.some(a => a.severity === 'LIFE_THREATENING')
    || s.activeProblems.some(p => ['SEVERE', 'LIFE_THREATENING'].includes(p.severity))

  const criticalLines = [
    ...s.allergies.filter(a => a.severity === 'LIFE_THREATENING').map(a => `Allergy — ${a.substance} (${a.reaction})`),
    ...s.activeProblems.filter(p => p.severity === 'LIFE_THREATENING').map(p => `Problem — ${p.title}`),
    ...s.activeProblems.filter(p => p.severity === 'SEVERE').map(p => `Severe — ${p.title}`),
  ]

  const lv  = s.recentVitals[0] ?? null
  const bmi = calcBMI(lv?.weight, lv?.height)

  const hasAllergyAlert = s.allergies.some(a => a.severity === 'LIFE_THREATENING')

  return (
    <div className="max-w-4xl mx-auto px-4 py-8 space-y-5">

      {/* ── Page header ── */}
      <div className="flex items-start justify-between flex-wrap gap-4">
        <div>
          <Link to={`/patients/${patientId}`} className="text-sm text-blue-600 hover:underline">
            ← Patient Profile
          </Link>
          <h1 className="text-2xl font-bold text-gray-900 mt-1">Medical Summary</h1>
          {s.patientName && (
            <div className="flex items-center gap-2 mt-1">
              <p className="text-gray-500 text-sm">{s.patientName}</p>
              {s.bloodGroup && s.bloodGroup !== 'UNKNOWN' && (
                <span className="text-xs font-bold bg-red-50 text-red-700 border border-red-200 px-2 py-0.5 rounded-full">
                  {s.bloodGroup}
                </span>
              )}
            </div>
          )}
        </div>
        <div className="flex items-start gap-4">
          <div className="text-right text-sm text-gray-500 space-y-0.5">
            {s.lastVisitDate && (
              <p>
                Last visit: <span className="font-semibold text-gray-700">{s.lastVisitDate}</span>
                <span className="ml-1 text-xs text-gray-400">({relativeDate(s.lastVisitDate)})</span>
              </p>
            )}
            {s.nextAppointmentDate && (
              <p className="text-blue-600 font-medium">
                Next appt: <span className="font-semibold">{s.nextAppointmentDate}</span>
                <span className="ml-1 text-xs text-blue-400">({futureDays(s.nextAppointmentDate)})</span>
              </p>
            )}
          </div>
          <button
            onClick={() => window.print()}
            className="shrink-0 text-xs font-medium bg-gray-100 hover:bg-gray-200 text-gray-600 px-3 py-2 rounded-lg transition-colors border border-gray-200"
          >
            🖨 Print
          </button>
        </div>
      </div>

      {/* ── Critical alert banner ── */}
      {hasCritical && (
        <div className="rounded-xl bg-red-50 border border-red-300 p-4 flex items-start gap-3">
          <span className="text-2xl shrink-0 mt-0.5">⚠️</span>
          <div>
            <p className="text-sm font-bold text-red-800 mb-1">Clinical Alert — Review Before Treatment</p>
            <ul className="space-y-0.5">
              {criticalLines.map((line, i) => (
                <li key={i} className="text-sm text-red-700">• {line}</li>
              ))}
            </ul>
          </div>
        </div>
      )}

      {/* ── Stat cards ── */}
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
        <StatCard count={s.activeProblems.length}    label="Active Problems"     color="orange" alert={s.activeProblems.some(p => ['SEVERE','LIFE_THREATENING'].includes(p.severity))} />
        <StatCard count={s.activeMedications.length} label="Active Medications"  color="blue"   />
        <StatCard count={s.allergies.length}         label="Known Allergies"     color="purple" alert={hasAllergyAlert} />
        <StatCard count={s.totalVisits}              label="Total Visits"        color="teal"   />
      </div>

      {/* ── Vitals ── */}
      <VitalsSection vitals={s.recentVitals} bmi={bmi} />

      {/* ── Problems + Medications side-by-side ── */}
      <div className="grid md:grid-cols-2 gap-5">
        <ProblemsSection    problems={s.activeProblems} />
        <MedicationsSection medications={s.activeMedications} />
      </div>

      {/* ── Allergies (full width) ── */}
      <AllergiesSection allergies={s.allergies} />

      {/* ── Footer ── */}
      <p className="text-xs text-gray-300 text-right">
        Generated {new Date().toLocaleString()} · Patient ID: {patientId}
      </p>
    </div>
  )
}
