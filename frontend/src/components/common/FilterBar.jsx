/**
 * Status / Gender / Blood Group filter bar for the patient list page.
 */
export default function FilterBar({ status, gender, bloodGroup, onStatusChange, onGenderChange, onBloodGroupChange }) {
  return (
    <div className="flex flex-wrap gap-3 items-center">

      <div className="flex items-center gap-2">
        <label htmlFor="filter-status" className="text-sm font-medium text-gray-600">
          Status
        </label>
        <select
          id="filter-status"
          className="form-input py-1.5 pr-8 text-sm"
          value={status}
          onChange={e => onStatusChange(e.target.value)}
          aria-label="Filter by status"
        >
          <option value="ACTIVE">Active</option>
          <option value="INACTIVE">Inactive</option>
          <option value="ALL">All</option>
        </select>
      </div>

      <div className="flex items-center gap-2">
        <label htmlFor="filter-gender" className="text-sm font-medium text-gray-600">
          Gender
        </label>
        <select
          id="filter-gender"
          className="form-input py-1.5 pr-8 text-sm"
          value={gender}
          onChange={e => onGenderChange(e.target.value)}
          aria-label="Filter by gender"
        >
          <option value="ALL">All</option>
          <option value="MALE">Male</option>
          <option value="FEMALE">Female</option>
          <option value="OTHER">Other</option>
        </select>
      </div>

      <div className="flex items-center gap-2">
        <label htmlFor="filter-blood" className="text-sm font-medium text-gray-600">
          Blood Group
        </label>
        <select
          id="filter-blood"
          className="form-input py-1.5 pr-8 text-sm"
          value={bloodGroup}
          onChange={e => onBloodGroupChange(e.target.value)}
          aria-label="Filter by blood group"
        >
          <option value="ALL">All</option>
          <option value="A_POS">A+</option>
          <option value="A_NEG">A−</option>
          <option value="B_POS">B+</option>
          <option value="B_NEG">B−</option>
          <option value="AB_POS">AB+</option>
          <option value="AB_NEG">AB−</option>
          <option value="O_POS">O+</option>
          <option value="O_NEG">O−</option>
          <option value="UNKNOWN">Unknown</option>
        </select>
      </div>
    </div>
  )
}
