import React from "react";
import { motion } from "framer-motion";
const Card = ({ title, desc }) => {
  return (
    <div className="shadow-md shadow-slate-400 border flex flex-col px-4 py-8 gap-3 rounded-sm">
      <h1 className="text-slate-900 text-xl font-bold"></h1>
      <p className="text-slate-700 text-sm">{desc}</p>
    </div>
  );
};

export default Card;
