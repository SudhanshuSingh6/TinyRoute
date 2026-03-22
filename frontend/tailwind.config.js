/** @type {import("tailwindcss").Config} */
export default {
  content: ["./index.html", "./src/**/*.{js,ts,jsx,tsx}"],
  theme: {
    extend: {
      backgroundImage: {
        "custom-gradient": "linear-gradient(to right, #3b82f6, #9333ea)",
      },
      colors: {
        btnColor:  "#3364F7",
        linkColor: "#2a5bd7",
        brand: {
          50:  "#eff6ff",
          100: "#dbeafe",
          500: "#3b82f6",
          600: "#3364F7",
          700: "#2a5bd7",
        },
      },
      boxShadow: {
        custom: "0 0 15px rgba(0, 0, 0, 0.3)",
        card:   "0 2px 16px rgba(0, 0, 0, 0.08)",
      },
      fontFamily: {
        montserrat: ["Montserrat", "sans-serif"],
      },
      fontSize: {
        "17": ["17px", { lineHeight: "1.5" }],
        "18": ["18px", { lineHeight: "1.5" }],
        "22": ["22px", { lineHeight: "1.3" }],
      },
      width: {
        "form-sm": "360px",
        "form-md": "450px",
        "card-sm": "320px",
        "card-md": "420px",
      },
      minHeight: {
        page: "calc(100vh - 64px)",
      },
      screens: {
        xs: "480px",
      },
    },
  },
  plugins: [],
};
