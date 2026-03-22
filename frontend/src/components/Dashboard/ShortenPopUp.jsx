import Modal from "@mui/material/Modal";
import CreateNewShorten from "./CreateNewShorten";

const ShortenPopUp = ({ open, setOpen, refetch }) => {
  return (
    <Modal
      open={open}
      onClose={() => setOpen(false)}
      aria-labelledby="create-short-url-modal"
    >
      <div className="flex justify-center items-center h-full w-full">
        <CreateNewShorten setOpen={setOpen} refetch={refetch} />
      </div>
    </Modal>
  );
};

export default ShortenPopUp;
