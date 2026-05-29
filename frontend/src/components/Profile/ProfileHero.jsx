import { useState } from "react";
import PropTypes from "prop-types";
import toast from "react-hot-toast";
import { FaRegEdit } from "react-icons/fa";
import Button from "../Common/Button";
import AvatarDisplay from "../Common/AvatarDisplay";
import { updateProfile } from "../../hooks/useQuery";
import { isValidHttpUrl } from "../../utils/helper";

const ProfileHero = ({ profile, initials, onSaved }) => {
  const [editing, setEditing] = useState(false);
  const [avatarDraft, setAvatarDraft] = useState("");
  const [bioDraft, setBioDraft] = useState("");
  const [avatarError, setAvatarError] = useState("");
  const [saving, setSaving] = useState(false);

  const openEdit = () => {
    setAvatarDraft(profile.avatarUrl ?? "");
    setBioDraft(profile.bio ?? "");
    setAvatarError("");
    setEditing(true);
  };

  const cancelEdit = () => {
    setEditing(false);
    setAvatarError("");
  };

  const handleSave = async () => {
    const trimmed = avatarDraft.trim();
    if (trimmed && !isValidHttpUrl(trimmed)) {
      setAvatarError("Enter a valid http/https URL, or leave blank to remove.");
      return;
    }
    setSaving(true);
    try {
      await updateProfile({ bio: bioDraft.trim(), avatarUrl: trimmed });
      toast.success("Profile updated.");
      setEditing(false);
      setAvatarError("");
      onSaved();
    } catch {
      toast.error("Could not save changes.");
    } finally {
      setSaving(false);
    }
  };

  const previewSrc = editing ? avatarDraft.trim() : profile.avatarUrl;

  return (
    <div className="bg-white border border-slate-200 rounded-xl shadow-card overflow-hidden">
      <div className="h-24 bg-custom-gradient" />

      <div className="px-6 pb-6 flex flex-col items-center -mt-10 gap-3">
        <div className="relative z-10 rounded-full bg-white">
          <AvatarDisplay src={previewSrc} initials={initials} size={80} />
        </div>

        <div className="text-center">
          <h1 className="text-xl font-bold font-montserrat text-slate-900">
            {profile.username}
          </h1>
          {profile.email && (
            <p className="text-slate-400 text-sm mt-0.5">{profile.email}</p>
          )}
        </div>

        {editing ? (
          <div className="w-full max-w-sm space-y-3">
            <div>
              <label className="text-xs font-semibold uppercase tracking-wider text-slate-400 block mb-1">
                Avatar URL
              </label>
              <input
                type="url"
                value={avatarDraft}
                onChange={(e) => {
                  setAvatarDraft(e.target.value);
                  setAvatarError("");
                }}
                placeholder="https://example.com/avatar.png"
                className={`w-full px-3 py-2 border rounded-md outline-none text-slate-700 text-sm ${
                  avatarError ? "border-red-400" : "border-slate-300 focus:border-btnColor"
                }`}
              />
              {avatarError && (
                <p className="text-xs text-red-500 mt-1">{avatarError}</p>
              )}
            </div>
            <div>
              <label className="text-xs font-semibold uppercase tracking-wider text-slate-400 block mb-1">
                Bio
              </label>
              <textarea
                rows={3}
                value={bioDraft}
                maxLength={300}
                onChange={(e) => setBioDraft(e.target.value)}
                placeholder="Write a short bio…"
                className="w-full px-3 py-2 border border-slate-300 focus:border-btnColor rounded-md outline-none text-slate-700 text-sm resize-none"
              />
              <p className="text-xs text-slate-400 text-right">{bioDraft.length}/300</p>
            </div>
            <div className="flex gap-2 justify-center">
              <Button variant="primary" size="sm" loading={saving} onClick={handleSave}>
                Save
              </Button>
              <Button variant="ghost" size="sm" disabled={saving} onClick={cancelEdit}>
                Cancel
              </Button>
            </div>
          </div>
        ) : (
          <div className="text-center max-w-sm">
            <p className="text-slate-600 text-sm leading-relaxed">
              {profile.bio?.trim() || (
                <span className="text-slate-400 italic">No bio yet.</span>
              )}
            </p>
            <button
              onClick={openEdit}
              className="mt-3 inline-flex items-center gap-1.5 text-xs font-semibold text-btnColor hover:opacity-75 transition-opacity"
            >
              <FaRegEdit className="text-xs" /> Edit Profile
            </button>
          </div>
        )}
      </div>
    </div>
  );
};

ProfileHero.propTypes = {
  profile: PropTypes.object.isRequired,
  initials: PropTypes.string.isRequired,
  onSaved: PropTypes.func.isRequired,
};

export default ProfileHero;
