import { FaLink, FaShareAlt, FaEdit, FaChartLine } from "react-icons/fa";
import { useNavigate } from "react-router-dom";
import Button from "../components/common/Button";

const FEATURES = [
  {
    icon: <FaLink className="text-blue-500 text-3xl" />,
    title: "Simple URL Shortening",
    desc: "Experience the ease of creating short, memorable URLs in just a few clicks. Our intuitive interface ensures you can start shortening URLs without any hassle.",
  },
  {
    icon: <FaShareAlt className="text-green-500 text-3xl" />,
    title: "Powerful Analytics",
    desc: "Gain insights into your link performance with our analytics dashboard. Track clicks and date-based data to optimize how you share.",
  },
  {
    icon: <FaEdit className="text-purple-500 text-3xl" />,
    title: "Enhanced Security",
    desc: "All shortened URLs are protected with advanced encryption, ensuring your data remains safe and secure at all times.",
  },
  {
    icon: <FaChartLine className="text-red-500 text-3xl" />,
    title: "Fast and Reliable",
    desc: "Enjoy lightning-fast redirects and high uptime. Your shortened URLs will always be available, ensuring a seamless experience for your users.",
  },
];

const AboutPage = () => {
  const navigate = useNavigate();

  return (
    <div className="lg:px-14 sm:px-8 px-5 min-h-page pt-2">
      <div className="bg-white w-full sm:py-10 py-8">
        <h1 className="sm:text-4xl text-slate-800 text-3xl font-bold font-montserrat italic mb-3">
          About TinyRoute
        </h1>
        <p className="text-gray-600 text-sm mb-8 xl:w-3/5 lg:w-4/6 sm:w-4/5 w-full leading-relaxed">
          TinyRoute simplifies URL shortening for efficient sharing. Easily
          generate, manage, and track your shortened links — all from one clean
          dashboard. Whether you&apos;re sharing links on social media, in emails,
          or with teammates, TinyRoute makes every link shorter and smarter.
        </p>

        <div className="space-y-6 xl:w-3/5 lg:w-4/6 sm:w-4/5 w-full">
          {FEATURES.map(({ icon, title, desc }) => (
            <div key={title} className="flex items-start gap-4">
              <div className="mt-1">{icon}</div>
              <div>
                <h2 className="sm:text-xl font-bold text-slate-800 mb-1">
                  {title}
                </h2>
                <p className="text-gray-600 text-sm leading-relaxed">{desc}</p>
              </div>
            </div>
          ))}
        </div>

        {/* CTA */}
        <div className="mt-10">
          <Button
            variant="primary"
            size="lg"
            onClick={() => navigate("/register")}
          >
            Get Started — It&apos;s Free
          </Button>
        </div>
      </div>
    </div>
  );
};

export default AboutPage;
