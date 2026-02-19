import { useNavigate } from 'react-router-dom'
import PatientStatusBadge from './PatientStatusBadge.jsx'

const GENDER_LABEL = { MALE: 'Male', FEMALE: 'Female', OTHER: 'Other' }

export default function PatientListRow({ patient }) {
  const navigate = useNavigate()
  const { patientId, firstName, lastName, age, gender, phone, status } = patient

  function handleClick() {
    navigate(`/patients/${patientId}`)
  }

  function handleKeyDown(e) {
    if (e.key === 'Enter' || e.key === ' ') {
      e.preventDefault()
      handleClick()
    }
  }

  return (
    <tr
      className="hover:bg-gray-50 cursor-pointer transition-colors"
      onClick={handleClick}
      onKeyDown={handleKeyDown}
      tabIndex={0}
      role="row"
      aria-label={`Patient ${firstName} ${lastName}, ID ${patientId}`}
    >
      <td className="px-4 py-3 text-sm font-mono text-blue-700 whitespace-nowrap">{patientId}</td>
      <td className="px-4 py-3 text-sm font-medium text-gray-900 whitespace-nowrap">
        {firstName} {lastName}
      </td>
      <td className="px-4 py-3 text-sm text-gray-600">{age}</td>
      <td className="px-4 py-3 text-sm text-gray-600">{GENDER_LABEL[gender] || gender}</td>
      <td className="px-4 py-3 text-sm text-gray-600 whitespace-nowrap">{phone}</td>
      <td className="px-4 py-3">
        <PatientStatusBadge status={status} />
      </td>
    </tr>
  )
}
