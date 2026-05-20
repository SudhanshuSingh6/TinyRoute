import { FaFacebook, FaTwitter, FaInstagram, FaLinkedin } from "react-icons/fa";
import Logo from "./Logo";

const Footer = () => {
  return (
    <footer className="bg-custom-gradient text-white py-8 z-40 relative">
      <div className="container mx-auto px-6 lg:px-14 flex flex-col lg:flex-row lg:justify-between items-center gap-4">
        <div className="text-center lg:text-left">
          <Logo size="md" variant="light" />
          <p className="text-blue-100 text-sm mt-2">
            Simplifying URL shortening for efficient sharing.
          </p>
        </div>

        <p className="text-blue-100 text-sm mt-4 lg:mt-0">
          &copy; {new Date().getFullYear()} TinyRoute. All rights reserved.
        </p>

        <div className="flex space-x-5 mt-4 lg:mt-0">
          {[
            { icon: <FaFacebook size={22} />, href: "#" },
            { icon: <FaTwitter size={22} />, href: "#" },
            { icon: <FaInstagram size={22} />, href: "#" },
            { icon: <FaLinkedin size={22} />, href: "#" },
          ].map(({ icon, href }, i) => (
            <a
              key={i}
              href={href}
              className="hover:text-blue-200 transition-colors duration-150"
            >
              {icon}
            </a>
          ))}
        </div>
      </div>
    </footer>
  );
};

export default Footer;
