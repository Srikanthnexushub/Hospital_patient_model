import { useState } from 'react'

const RISK_COLOURS = {
  LOW:        { ring: 'border-green-400',  bg: 'bg-green-50',  text: 'text-green-800',  label: 'LOW'        },
  LOW_MEDIUM: { ring: 'border-yellow-400', bg: 'bg-yellow-50', text: 'text-yellow-800', label: 'LOW-MEDIUM' },
  MEDIUM:     { ring: 'border-orange-400', bg: 'bg-orange-50', text: 'text-orange-800', label: 'MEDIUM'     },
  HIGH:       { ring: 'border-red-500',    bg: 'bg-red-50',    text: 'text-red-800',    label: 'HIGH'       },
  NO_DATA:    { ring: 'border-gray-300',   bg: 'bg-gray-50',   text: 'text-gray-500',   label: 'NO DATA'    },
}

/**
 * Compact NEWS2 score card with expandable component breakdown.
 * Props: news2 – the API response object from GET /patients/{id}/news2
 */
export default function News2ScoreCard({ news2 }) {
  const [expanded, setExpanded] = useState(false)

  if (!news2) return null

  const riskLevel = news2.riskLevel ?? 'NO_DATA'
  const style = RISK_COLOURS[riskLevel] ?? RISK_COLOURS.NO_DATA

  return (
    <div className={`rounded-lg border-2 ${style.ring} ${style.bg} px-4 py-3 min-w-[140px]`}>
      <div className="flex items-center justify-between gap-3">
        <div>
          <p className="text-[10px] font-medium text-gray-500 uppercase tracking-wide">NEWS2</p>
          <p className={`text-2xl font-bold ${style.text}`}>
            {news2.totalScore != null ? news2.totalScore : '—'}
          </p>
          <p className={`text-xs font-semibold ${style.text}`}>{style.label}</p>
        </div>
        {news2.components?.length > 0 && (
          <button
            onClick={() => setExpanded(e => !e)}
            className="text-xs text-gray-400 hover:text-gray-600 underline shrink-0 self-start mt-1"
          >
            {expanded ? 'Hide' : 'Details'}
          </button>
        )}
      </div>

      {news2.recommendation && (
        <p className="text-xs text-gray-600 mt-1 italic">{news2.recommendation}</p>
      )}

      {expanded && news2.components?.length > 0 && (
        <div className="mt-3 border-t border-gray-200 pt-2 space-y-1">
          {news2.components.map((c, i) => (
            <div key={i} className="flex items-center justify-between text-xs">
              <span className="text-gray-500">
                {c.parameter?.replace(/_/g, ' ')}
                {c.defaulted && <span className="ml-1 text-gray-400">(default)</span>}
              </span>
              <span className="flex items-center gap-2">
                <span className="text-gray-700">{c.value != null ? `${c.value}${c.unit ? ' ' + c.unit : ''}` : '—'}</span>
                <span className={`font-bold w-4 text-center ${c.score >= 3 ? 'text-red-600' : c.score >= 2 ? 'text-orange-600' : c.score >= 1 ? 'text-yellow-600' : 'text-green-600'}`}>
                  {c.score}
                </span>
              </span>
            </div>
          ))}
        </div>
      )}

      {riskLevel === 'NO_DATA' && (
        <p className="text-xs text-gray-400 mt-1">{news2.message ?? 'No vitals on record'}</p>
      )}

      {news2.computedAt && (
        <p className="text-[10px] text-gray-400 mt-1">
          {new Date(news2.computedAt).toLocaleString()}
        </p>
      )}
    </div>
  )
}
