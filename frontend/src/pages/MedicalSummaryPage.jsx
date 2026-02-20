import { useParams, Link } from 'react-router-dom'
import { useMedicalSummary } from '../hooks/useEmr.js'
import LoadingSpinner from '../components/common/LoadingSpinner.jsx'

function SectionCard({ title, children }) {
  return (
    <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-5">
      <h2 className="text-base font-semibold text-gray-900 mb-3">{title}</h2>
      {children}
    </div>
  )
}

function EmptyState({ message }) {
  return <p className="text-sm text-gray-500">{message}</p>
}

export default function MedicalSummaryPage() {
  const { patientId } = useParams()
  const { data: summary, isLoading, isError, error } = useMedicalSummary(patientId)

  if (isLoading) return <div className="max-w-3xl mx-auto px-4 py-8"><LoadingSpinner /></div>

  if (isError) {
    const is403 = error?.response?.status === 403
    const is404 = error?.response?.status === 404
    return (
      <div className="max-w-3xl mx-auto px-4 py-8">
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

  const severityColours = {
    MILD: 'bg-green-100 text-green-800',
    MODERATE: 'bg-yellow-100 text-yellow-800',
    SEVERE: 'bg-red-100 text-red-800',
    LIFE_THREATENING: 'bg-red-200 text-red-900',
  }

  return (
    <div className="max-w-3xl mx-auto px-4 py-8 space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <Link to={`/patients/${patientId}`} className="text-sm text-blue-600 hover:underline">← Patient Profile</Link>
          <h1 className="text-2xl font-bold text-gray-900 mt-1">Medical Summary</h1>
        </div>
        <div className="text-right text-sm text-gray-500">
          <p>Total Visits: <span className="font-semibold text-gray-900">{summary.totalVisits}</span></p>
          {summary.lastVisitDate && (
            <p>Last Visit: <span className="font-semibold text-gray-900">{summary.lastVisitDate}</span></p>
          )}
        </div>
      </div>

      {/* Active Problems */}
      <SectionCard title="Active Problems">
        {summary.activeProblems.length === 0 ? <EmptyState message="No active problems." /> : (
          <div className="divide-y divide-gray-100">
            {summary.activeProblems.map(p => (
              <div key={p.id} className="py-2 flex items-start justify-between gap-3">
                <div>
                  <p className="text-sm font-medium text-gray-900">{p.title}</p>
                  {p.icdCode && <p className="text-xs text-gray-400">ICD: {p.icdCode}</p>}
                </div>
                <span className={`inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium ${severityColours[p.severity] ?? 'bg-gray-100 text-gray-600'}`}>
                  {p.severity}
                </span>
              </div>
            ))}
          </div>
        )}
      </SectionCard>

      {/* Active Medications */}
      <SectionCard title="Active Medications">
        {summary.activeMedications.length === 0 ? <EmptyState message="No active medications." /> : (
          <div className="divide-y divide-gray-100">
            {summary.activeMedications.map(m => (
              <div key={m.id} className="py-2">
                <p className="text-sm font-medium text-gray-900">{m.medicationName}
                  {m.genericName && <span className="text-xs text-gray-400 ml-1">({m.genericName})</span>}
                </p>
                <p className="text-xs text-gray-600">{m.dosage} · {m.frequency} · {m.route}</p>
              </div>
            ))}
          </div>
        )}
      </SectionCard>

      {/* Allergies */}
      <SectionCard title="Known Allergies">
        {summary.allergies.length === 0 ? <EmptyState message="No known allergies." /> : (
          <div className="divide-y divide-gray-100">
            {summary.allergies.map(a => (
              <div key={a.id} className="py-2 flex items-center gap-3">
                <span className={`inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium ${severityColours[a.severity] ?? 'bg-gray-100 text-gray-600'}`}>
                  {a.severity}
                </span>
                <div>
                  <p className="text-sm font-medium text-gray-900">{a.substance}</p>
                  <p className="text-xs text-gray-500">{a.reaction}</p>
                </div>
              </div>
            ))}
          </div>
        )}
      </SectionCard>

      {/* Recent Vitals */}
      <SectionCard title="Recent Vitals (last 5)">
        {summary.recentVitals.length === 0 ? <EmptyState message="No vitals recorded." /> : (
          <div className="space-y-3">
            {summary.recentVitals.map((v, idx) => (
              <div key={v.id ?? idx} className="bg-gray-50 rounded p-3 text-sm">
                <p className="text-xs text-gray-400 mb-1">{v.recordedAt?.slice(0, 16).replace('T', ' ')} · {v.recordedBy}</p>
                <div className="flex flex-wrap gap-x-4 gap-y-1">
                  {v.bloodPressureSystolic != null && <span>BP: {v.bloodPressureSystolic}/{v.bloodPressureDiastolic}</span>}
                  {v.heartRate != null && <span>HR: {v.heartRate}</span>}
                  {v.temperature != null && <span>Temp: {v.temperature}°C</span>}
                  {v.weight != null && <span>Wt: {v.weight}kg</span>}
                  {v.oxygenSaturation != null && <span>SpO₂: {v.oxygenSaturation}%</span>}
                </div>
              </div>
            ))}
          </div>
        )}
      </SectionCard>
    </div>
  )
}
